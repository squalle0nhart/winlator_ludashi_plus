package com.winlator.cmod;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.R;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.XEnvironment;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class TerminalActivity extends AppCompatActivity {
    private TextView outputTextView;
    private EditText commandInput;
    private Button executeButton;
    private XEnvironment xEnvironment;
    private ImageFs imageFs;

    // Define a generic launcher interface
    private GuestProgramLauncherComponent launcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        outputTextView = findViewById(R.id.outputTextView);
        commandInput = findViewById(R.id.commandInput);
        executeButton = findViewById(R.id.executeButton);

        // Initialize ImageFs and XEnvironment
        imageFs = ImageFs.find(this);
        if (!imageFs.isValid()) {
            outputTextView.setText("Error: Invalid ImageFs.");
            return;
        }
        xEnvironment = new XEnvironment(this, imageFs);

        // Initialize ContentsManager
        ContentsManager contentsManager = new ContentsManager(this);

        // Use the shared launcher component implementation.
        launcher = new GuestProgramLauncherComponent(contentsManager, null, null);

        // Add the launcher to XEnvironment
        xEnvironment.addComponent(launcher);

        // Set execute permissions for all binaries in the bin folder
        setExecutePermissionsForBinaries();

        // Set up executeButton onClickListener
        executeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String command = commandInput.getText().toString();
                if (!command.isEmpty()) {
                    executeCommand(command);
                }
            }
        });
    }

    private void setExecutePermissionsForBinaries() {
        File binDir = new File(imageFs.getRootDir(), "usr/bin");
        if (binDir.isDirectory()) {
            File[] files = binDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    FileUtils.chmod(file, 0755); // Set permissions to make it executable
                }
            }
        }
    }

    // Modify `executeCommand` to handle specific commands first
    private void executeCommand(String command) {
        // Avoid interactive shells initially
        if (command.equals("bash") || command.equals("dash")) {
            outputTextView.append("\n$ " + command + "\nInteractive shells are unsupported.\n");
            return;
        }

        // Add full path for the command to run within imageFs
        String fullCommand = command;

        // Execute and capture output through the local shell.
        String output = runShellCommand(fullCommand);
        outputTextView.append("\n$ " + command + "\n" + output);
        commandInput.setText("");
    }

    private String runShellCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});

            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = out.readLine()) != null) output.append(line).append('\n');
                while ((line = err.readLine()) != null) output.append(line).append('\n');
            }
            process.waitFor();
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage());
        }
        return output.toString();
    }
}
