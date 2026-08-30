package com.winlator.cmod;

import androidx.appcompat.app.AppCompatActivity;

public class EmbeddedContainerSectionFragment extends ContainerSectionFragment {
    public EmbeddedContainerSectionFragment(int containerId, int section) {
        super(containerId, section);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) {
            ((AppCompatActivity) requireActivity())
                    .getSupportActionBar()
                    .setTitle(R.string.configure_container);
        }
    }
}
