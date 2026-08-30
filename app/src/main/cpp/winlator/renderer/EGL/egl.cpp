#include "egl.hpp"

void EGLRenderer::start() {
    stopped = false;
    requestUpdate = false;
    renderingThread = std::thread(&EGLRenderer::renderingThreadLoop, this);
}

void EGLRenderer::queueEvent(std::function<void()> func) {
    if (stopped.load()) return;
    auto lock = renderLock.lock();
    eventQueue.push(func);
    renderLock.notify();
}

void EGLRenderer::requestRenderer() {
    if (stopped.load()) return;
    auto lock = renderLock.lock();
    requestUpdate = true;
    renderLock.notify();
}

void EGLRenderer::createSurface(ANativeWindow *window) {
    if (stopped.load()) {
        if (window) ANativeWindow_release(window);
        return;
    }
    auto lock = renderLock.lock();
    this->window = window;
    state = State::CREATE_SURFACE;
    renderLock.notify();
    renderLock.wait(lock, [&]{ return stopped.load() || state == State::NONE; });
}

void EGLRenderer::destroySurface() {
    if (stopped.load()) return;
    auto lock = renderLock.lock();
    state = State::DESTROY_SURFACE;
    renderLock.notify();
    renderLock.wait(lock, [&]{ return stopped.load() || state == State::NONE; });
}

void EGLRenderer::changeSurface(int width, int height) {
    if (stopped.load()) return;
    auto lock = renderLock.lock();
    this->surfaceWidth = width;
    this->surfaceHeight = height;
    state = State::CHANGE_SURFACE;
    renderLock.notify();
    renderLock.wait(lock, [&]{ return stopped.load() || state == State::NONE; });
}

void EGLRenderer::renderingThreadLoop() {
    bool createEGLSurface = false;
    bool surfaceChanged = false;
    bool hasSurface = false;
    bool paused = false;
    bool badSurface = false;
    bool badContext = false;

    xServer->env = cache->getEnv();

    while (true) {
        std::function<void()> func = nullptr;
        bool requestRender = false;
        auto lock = renderLock.lock();
        renderLock.wait(lock, [&]{
            if (paused) {
                return stopped.load() || state != State::NONE;
            } else {
                return stopped.load() || state != State::NONE ||
                       (hasSurface && surfaceChanged &&
                        (!eventQueue.empty() || requestUpdate.load()));
            }
        });

        if (stopped.load()) {
            lock.unlock();
            if (surface != EGL_NO_SURFACE) destroyEGLSurface();
            if (context != EGL_NO_CONTEXT) destroyEGLContext();
            if (window) {
                ANativeWindow_release(window);
                window = nullptr;
            }
            if (display != EGL_NO_DISPLAY) {
                eglTerminate(display);
                display = EGL_NO_DISPLAY;
            }
            cache->detachEnv(xServer->env);
            return;
        }

        State currentState = state;
        state = State::NONE;

        if (currentState == State::PAUSE) {
            paused = true;
            renderLock.notify();
        }
        else if (currentState == State::RESUME) {
            paused = false;
            renderLock.notify();
        }
        else if (currentState == State::CREATE_SURFACE) {
            hasSurface = true;
            renderLock.notify();
        }
        else if (currentState == State::CHANGE_SURFACE) {
            viewTransformation.update(surfaceWidth, surfaceHeight,
                    windowManager->getRootWindow()->width,
                    windowManager->getRootWindow()->height);
            viewportNeedsUpdate = true;
            surfaceChanged = true;
            requestUpdate = true;
            renderLock.notify();
        }
        else if (currentState == State::DESTROY_SURFACE) {
            hasSurface = false;
            surfaceChanged = false;
            if (surface != EGL_NO_SURFACE) destroyEGLSurface();
            if (window) {
                ANativeWindow_release(window);
                window = nullptr;
            }
            renderLock.notify();
        }

        if (badContext) {
            if (surface != EGL_NO_SURFACE) destroyEGLSurface();
            destroyEGLContext();
            badContext = false;
            badSurface = false;
        }
        else if (badSurface) {
            if (surface != EGL_NO_SURFACE) destroyEGLSurface();
            badSurface = false;
        }

        if (!eventQueue.empty() && hasSurface && surfaceChanged && !paused) {
            func = std::move(eventQueue.front());
            eventQueue.pop();
        }
        else if (requestUpdate.load() && hasSurface && surfaceChanged && !paused) {
            if (context == EGL_NO_CONTEXT) init();
            if (context != EGL_NO_CONTEXT && surface == EGL_NO_SURFACE)
                createEGLSurface = true;
            requestRender = true;
            requestUpdate = false;
        }

        lock.unlock();

        if (func) {
            func();
            continue;
        }

        if (createEGLSurface) {
            this->createEGLSurface(this->window);
            createEGLSurface = false;
            if (surface == EGL_NO_SURFACE) requestRender = false;
        }

        if (requestRender && surface != EGL_NO_SURFACE) {
            EGLBoolean result = drawFrame();
            if (result == EGL_FALSE) {
                EGLint error = eglGetError();
                badSurface = error == EGL_BAD_SURFACE;
                badContext = error == EGL_CONTEXT_LOST;
                if (badSurface || badContext) requestUpdate = true;
            }
        }
    }
}

EGLBoolean EGLRenderer::drawFrame() {
    if (toggleFullscreen) {
        fullscreen = !fullscreen;
        toggleFullscreen = false;
        viewportNeedsUpdate = true;
    }

    if (viewportNeedsUpdate && magnifierEnabled) {
        if (fullscreen) {
            glViewport(0, 0, surfaceWidth, surfaceHeight);
        }
        else {
            glViewport(viewTransformation.viewOffsetX, viewTransformation.viewOffsetY, viewTransformation.viewWidth, viewTransformation.viewHeight);
        }

        viewportNeedsUpdate = false;
    }

    glClear(GL_COLOR_BUFFER_BIT);

    if (magnifierEnabled) {
        float pointerX = 0;
        float pointerY = 0;
        float magnifierZoom = !screenOffsetYRelativeToCursor ? this->magnifierZoom : 1.0f;

        if (magnifierZoom != 1.0f) {
            pointerX = std::clamp(cursorManager->pointer.posX * magnifierZoom - windowManager->getRootWindow()->width * 0.5f, 0.0f, windowManager->getRootWindow()->width * std::abs(1.0f - magnifierZoom));
        }

        if (screenOffsetYRelativeToCursor || magnifierZoom != 1.0f) {
            float scaleY = magnifierZoom != 1.0f ? std::abs(1.0f - magnifierZoom) : 0.5f;
            float offsetY = windowManager->getRootWindow()->height * (screenOffsetYRelativeToCursor ? 0.25f : 0.5f);
            pointerY = std::clamp(cursorManager->pointer.posY * magnifierZoom - offsetY, 0.0f, windowManager->getRootWindow()->height * scaleY);
        }

        XForm::makeTransform(tmpXForm2, -pointerX, -pointerY, magnifierZoom, magnifierZoom, 0.0f);
    } else {
        if (!fullscreen) {
            int pointerY = 0;
            if (screenOffsetYRelativeToCursor) {
                uint16_t halfScreenHeight = (uint16_t)(windowManager->getRootWindow()->height / 2);
                pointerY = std::clamp<int>(cursorManager->pointer.posY - halfScreenHeight / 2, 0, halfScreenHeight);
            }

            XForm::makeTransform(tmpXForm2, viewTransformation.sceneOffsetX, viewTransformation.sceneOffsetY - pointerY, viewTransformation.sceneScaleX, viewTransformation.sceneScaleY, 0.0f);
            glEnable(GL_SCISSOR_TEST);
            glScissor(viewTransformation.viewOffsetX, viewTransformation.viewOffsetY, viewTransformation.viewWidth, viewTransformation.viewHeight);

        } else {
            XForm::identity(tmpXForm2);
        }
    }

    drawableShader->use();
    glUniform2f(drawableShader->getUniformLoc("viewSize"), windowManager->getRootWindow()->width, windowManager->getRootWindow()->height);

    renderWindows();
    if (cursorVisible) renderCursor();

    drawableShader->disable();

    if (!magnifierEnabled && !fullscreen) {
        glDisable(GL_SCISSOR_TEST);
    }

    return eglSwapBuffers(display, surface);
}

void EGLRenderer::renderWindows() {
    for (const auto& renderableWindow : renderableWindows) {
        if (renderableWindow == nullptr) continue;

        if (renderableWindow->window->hasDirectContents())
            renderDrawable(renderableWindow->window->currentDirectContent,
                renderableWindow->rootX + renderableWindow->window->directContentOffsetX,
                renderableWindow->rootY + renderableWindow->window->directContentOffsetY, true);
        else
            renderDrawable(renderableWindow->content, renderableWindow->rootX, renderableWindow->rootY, true);
    }
}

void EGLRenderer::renderCursor() {
    jobject pointWindowObj = xServer->env->CallObjectMethod(xServer->inputDeviceManager, cache->getPointWindow);
    if (!pointWindowObj) return;
    jint id = xServer->env->GetIntField(pointWindowObj, cache->windowID);
    xServer->env->DeleteLocalRef(pointWindowObj);
    auto pointWindow = windowManager->getWindow(id);
    auto cursor = (pointWindow != nullptr) ? pointWindow->cursor : nullptr;
    int x = std::clamp(cursorManager->pointer.posX, 0, windowManager->getRootWindow()->width - 1);
    int y = std::clamp(cursorManager->pointer.posY, 0, windowManager->getRootWindow()->height - 1);

    if (cursor != nullptr) {
        if (cursor->visible)
            renderDrawable(cursor->image.get(), x - cursor->hotspotX, y - cursor->hotspotY, false);
    }
    else {
        renderDrawable(cursorManager->getRootCursor()->image.get(), x, y, false);
    }
}

void EGLRenderer::renderDrawable(Drawable *drawable, int x, int y, bool isWindow) {
    if (drawable == nullptr || (drawable->data == nullptr && drawable->ahb == nullptr)) return;

    if (drawable->textureId < 0) {
        if (drawable->ahb != nullptr)
            drawable->textureId = allocateTextureDirect(drawable->ahb);
        else
            drawable->textureId = allocateTexture(drawable->width, drawable->height);
    }
    else if (drawable->sizeChanged) {
        if (drawable->ahb != nullptr) {
            if (drawable->textureId > 0)
                destroyTexture(drawable->textureId);
            drawable->textureId = allocateTextureDirect(drawable->ahb);
        } else {
            reallocateTexture(drawable->textureId, drawable->width, drawable->height);
        }
        drawable->sizeChanged = false;
    }

    if (drawable->textureId <= 0) return;

    if (drawable->isDirty && drawable->data != nullptr) {
        updateTextureDrawable(drawable->textureId, drawable->width, drawable->height, drawable->data);
        drawable->isDirty = false;
    }

    XForm::set(tmpXForm1, x, y, drawable->width, drawable->height);
    XForm::multiply(tmpXForm1, tmpXForm1, tmpXForm2);

    renderDrawable(drawable->textureId, 6, tmpXForm1, isWindow);
}

void EGLRenderer::updateScene() {
    renderableWindows.clear();
    collectRenderableWindows(windowManager->getRootWindow(), windowManager->getRootWindow()->x, windowManager->getRootWindow()->y);
}

void EGLRenderer::collectRenderableWindows(Window *window, int x, int y) {
    if (!window->mapped) return;
    if (window != windowManager->getRootWindow()) {
        bool viewable = xServer->env->CallBooleanMethod(window->attributes, cache->windowAttributesIsEnabled);

        if (viewable) {
            auto renderableWindow = std::make_unique<struct RenderableWindow>();
            renderableWindow->rootX = x;
            renderableWindow->rootY = y;
            renderableWindow->content = window->drawable.get();
            renderableWindow->window = window;
            renderableWindows.push_back(std::move(renderableWindow));
        }
    }

    for (const auto& child : window->children) {
        collectRenderableWindows(child, child->x + x, child->y + y);
    }
}

void EGLRenderer::updateWindowPosition(Window* window) {
    for (auto& renderableWindow : renderableWindows) {
        if (renderableWindow->window == window) {
            renderableWindow->rootX = window->getRootX();
            renderableWindow->rootY = window->getRootY();
            break;
        }
    }
}

void EGLRenderer::init() {
    EGLBoolean result;

    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        printf("Failed to get default display");
        return;
    }

    EGLint major, minor;
    result = eglInitialize(display, &major, &minor);
    if (result != EGL_TRUE) {
        printf("Failed to initialize egl");
        return;
    }

    printf("Initialized egl major %d minor %d", major, minor);

    int num_configs;

    const EGLint attrib_list[] = {
        EGL_RED_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 0,
        EGL_STENCIL_SIZE, 0,
        EGL_NONE
    };

    result = eglChooseConfig(display, attrib_list, &config, 1, &num_configs);
    if (result != EGL_TRUE || num_configs < 0) {
        printf("Failed to find suitable egl config");
        return;
    }

    const EGLint ctx_attrib_list[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };

    eglBindAPI(EGL_OPENGL_ES_API);

    context = eglCreateContext(display, config, EGL_NO_CONTEXT, ctx_attrib_list);
    if (context == EGL_NO_CONTEXT) {
        printf("Failed to create egl context");
        return;
    }
}

void EGLRenderer::createEGLSurface(ANativeWindow *window) {
    EGLBoolean result;

    surface = eglCreateWindowSurface(display, config, window, nullptr);
    if (surface == EGL_NO_SURFACE) {
        printf("Failed to create window surface");
        return;
    }

    result = eglMakeCurrent(display, surface, surface, context);
    if (result != EGL_TRUE) {
        printf("Failed to make context current");
        eglDestroySurface(display, surface);
        surface = EGL_NO_SURFACE;
        return;
    }

    glFrontFace(GL_CCW);
    glDisable(GL_CULL_FACE);

    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

    if (!drawableShader) drawableShader = new DrawableShader();
}

void EGLRenderer::renderDrawable(int textureId, int length, float xform[], bool isFromWindow) {
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glUniform1i(drawableShader->getUniformLoc("texture"), 0);
    glUniform1fv(drawableShader->getUniformLoc("xform"), length, xform);
    glUniform1i(drawableShader->getUniformLoc("is_cursor"), isFromWindow ? 0 : 1);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindTexture(GL_TEXTURE_2D, 0);
}

int EGLRenderer::allocateTextureDirect(AHardwareBuffer* hardwareBuffer) {
    if (!hardwareBuffer || display == EGL_NO_DISPLAY) return -1;

    const EGLint attribList[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(hardwareBuffer);
    if (!clientBuffer) return -1;

    EGLImageKHR imageKHR = eglCreateImageKHR(
            display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attribList);
    if (imageKHR == EGL_NO_IMAGE_KHR) return -1;

    GLuint textureId = 0;
    glGenTextures(1, &textureId);
    if (textureId == 0) {
        eglDestroyImageKHR(display, imageKHR);
        return -1;
    }

    while (glGetError() != GL_NO_ERROR) {}
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);
    if (glGetError() != GL_NO_ERROR) {
        glDeleteTextures(1, &textureId);
        eglDestroyImageKHR(display, imageKHR);
        return -1;
    }

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filterMode == 1 ? GL_NEAREST : GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filterMode == 1 ? GL_NEAREST : GL_LINEAR);

    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, imageKHR);
    if (glGetError() != GL_NO_ERROR) {
        glBindTexture(GL_TEXTURE_2D, 0);
        glDeleteTextures(1, &textureId);
        eglDestroyImageKHR(display, imageKHR);
        return -1;
    }

    glBindTexture(GL_TEXTURE_2D, 0);
    eglDestroyImageKHR(display, imageKHR);
    return static_cast<int>(textureId);
}

int EGLRenderer::allocateTexture(int width, int height) {
    int textureId;
    glGenTextures(1, (GLuint *)&textureId);

    glActiveTexture(GL_TEXTURE0);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glBindTexture(GL_TEXTURE_2D, textureId);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_BGRA_EXT, width, height, 0, GL_BGRA_EXT, GL_UNSIGNED_BYTE, nullptr);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filterMode == 1 ? GL_NEAREST : GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filterMode == 1 ? GL_NEAREST : GL_LINEAR);

    glBindTexture(GL_TEXTURE_2D, 0);

    return textureId;
}

int EGLRenderer::reallocateTexture(int textureId, int width, int height) {
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_BGRA_EXT, width, height, 0, GL_BGRA_EXT, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);
    return textureId;
}

void EGLRenderer::destroyTexture(int textureId) {
    glDeleteTextures(1, (GLuint *)&textureId);
}

void EGLRenderer::updateTextureDrawable(int textureId, int width, int height, void *data) {
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA_EXT, GL_UNSIGNED_BYTE, data);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void EGLRenderer::destroyEGLSurface() {
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(display, surface);
    surface = EGL_NO_SURFACE;
}

void EGLRenderer::destroyEGLContext() {
    if (drawableShader) {
        delete drawableShader;
        drawableShader = nullptr;
    }
    eglDestroyContext(display, context);
    context = EGL_NO_CONTEXT;
}

void EGLRenderer::stop() {
    if (stopped.exchange(true)) return;
    renderLock.notify();
    if (renderingThread.joinable()) renderingThread.join();
}

void EGLRenderer::pause() {
    if (stopped.load()) return;
    auto lock = renderLock.lock();
    state = State::PAUSE;
    renderLock.notify();
    renderLock.wait(lock, [&]{ return stopped.load() || state == State::NONE; });
}

void EGLRenderer::resume() {
    if (stopped.load()) return;
    auto lock = renderLock.lock();
    state = State::RESUME;
    renderLock.notify();
    renderLock.wait(lock, [&]{ return stopped.load() || state == State::NONE; });
}
