package org.opcoach.mailmcp.config;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ManagerUiApplication {

    private final ServerRegistry registry = ServerRegistry.defaultRegistry();
    private final ServerProcessManager processManager = ServerProcessManager.currentApplication();
    private final KeychainSecretStore secretStore = new KeychainSecretStore();
    private final ServerTableModel tableModel = new ServerTableModel();

    private JFrame frame;
    private JTable table;
    private JTextField profileField;
    private JTextField portField;
    private JTextField imapHostField;
    private JTextField imapPortField;
    private JComboBox<ConnectionSecurity> imapSecurityField;
    private JTextField smtpHostField;
    private JTextField smtpPortField;
    private JComboBox<ConnectionSecurity> smtpSecurityField;
    private JTextField usernameField;
    private JTextField fromAddressField;
    private JTextField fromNameField;
    private JTextField sentMailboxField;
    private JPasswordField passwordField;
    private JLabel statusLabel;

    private ManagerUiApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ManagerUiApplication().show());
    }

    private void show() {
        frame = new JFrame("OPCoach MCP Mail");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.add(toolbar(), BorderLayout.NORTH);
        frame.add(splitPane(), BorderLayout.CENTER);
        statusLabel = new JLabel("Ready.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.setSize(1040, 680);
        frame.setLocationRelativeTo(null);
        refresh();
        newProfile();
        frame.setVisible(true);
        new Timer(3000, _ -> refreshStatuses()).start();
    }

    private JPanel toolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newButton = new JButton("New");
        JButton saveButton = new JButton("Save");
        JButton startButton = new JButton("Start");
        JButton stopButton = new JButton("Stop");
        JButton copyButton = new JButton("Copy URL");
        JButton refreshButton = new JButton("Refresh");

        newButton.addActionListener(_ -> newProfile());
        saveButton.addActionListener(_ -> saveCurrentProfile());
        startButton.addActionListener(_ -> startCurrentProfile());
        stopButton.addActionListener(_ -> stopSelectedProfile());
        copyButton.addActionListener(_ -> copySelectedUrl());
        refreshButton.addActionListener(_ -> refresh());

        panel.add(newButton);
        panel.add(saveButton);
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(copyButton);
        panel.add(refreshButton);
        return panel;
    }

    private JSplitPane splitPane() {
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedProfile();
            }
        });
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Registered MCP mail servers"));

        JPanel form = formPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, form);
        splitPane.setResizeWeight(0.42);
        return splitPane;
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Profile configuration"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        profileField = new JTextField();
        portField = new JTextField();
        imapHostField = new JTextField();
        imapPortField = new JTextField("993");
        imapSecurityField = new JComboBox<>(ConnectionSecurity.values());
        smtpHostField = new JTextField();
        smtpPortField = new JTextField("465");
        smtpSecurityField = new JComboBox<>(ConnectionSecurity.values());
        usernameField = new JTextField();
        fromAddressField = new JTextField();
        fromNameField = new JTextField();
        sentMailboxField = new JTextField("INBOX.Sent");
        passwordField = new JPasswordField();

        int row = 0;
        add(panel, c, row++, "Profile", profileField);
        add(panel, c, row++, "Local MCP port", portField);
        add(panel, c, row++, "IMAP host", imapHostField);
        add(panel, c, row++, "IMAP port", imapPortField);
        add(panel, c, row++, "IMAP security", imapSecurityField);
        add(panel, c, row++, "SMTP host", smtpHostField);
        add(panel, c, row++, "SMTP port", smtpPortField);
        add(panel, c, row++, "SMTP security", smtpSecurityField);
        add(panel, c, row++, "Email username", usernameField);
        add(panel, c, row++, "Sender address", fromAddressField);
        add(panel, c, row++, "Sender name", fromNameField);
        add(panel, c, row++, "Sent folder", sentMailboxField);
        add(panel, c, row++, "Password or app password", passwordField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveStart = new JButton("Save and start");
        JButton copy = new JButton("Copy this URL");
        saveStart.addActionListener(_ -> startCurrentProfile());
        copy.addActionListener(_ -> copyCurrentUrl());
        buttons.add(saveStart);
        buttons.add(copy);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        panel.add(buttons, c);

        return panel;
    }

    private static void add(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component component) {
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(component, c);
    }

    private void refresh() {
        tableModel.setRows(registry.list());
        refreshStatuses();
    }

    private void refreshStatuses() {
        tableModel.fireTableDataChanged();
    }

    private void newProfile() {
        profileField.setText("default");
        portField.setText(Integer.toString(firstFreePort(8095)));
        imapHostField.setText("imap.example.com");
        imapPortField.setText("993");
        imapSecurityField.setSelectedItem(ConnectionSecurity.SSL_TLS);
        smtpHostField.setText("smtp.example.com");
        smtpPortField.setText("465");
        smtpSecurityField.setSelectedItem(ConnectionSecurity.SSL_TLS);
        usernameField.setText("training@example.com");
        fromAddressField.setText("training@example.com");
        fromNameField.setText("MCP Training");
        sentMailboxField.setText("INBOX.Sent");
        passwordField.setText("");
        table.clearSelection();
        setStatus("New profile.");
    }

    private void loadSelectedProfile() {
        ServerRegistration registration = selectedRegistration();
        if (registration == null) {
            return;
        }
        profileField.setText(registration.profile());
        portField.setText(Integer.toString(registration.port()));
        passwordField.setText("");
        if (Files.exists(registration.configFile())) {
            try {
                MailConfiguration configuration = new ConfigurationLoader(registration.configFile()).load(registration.profile());
                imapHostField.setText(configuration.imap().host());
                imapPortField.setText(Integer.toString(configuration.imap().port()));
                imapSecurityField.setSelectedItem(configuration.imap().security());
                smtpHostField.setText(configuration.smtp().host());
                smtpPortField.setText(Integer.toString(configuration.smtp().port()));
                smtpSecurityField.setSelectedItem(configuration.smtp().security());
                usernameField.setText(configuration.username());
                fromAddressField.setText(configuration.fromAddress());
                fromNameField.setText(configuration.fromName());
                sentMailboxField.setText(configuration.sentMailbox());
                setStatus("Loaded " + registration.profile() + ".");
            } catch (ConfigurationException exception) {
                showError(exception);
            }
        }
    }

    private void saveCurrentProfile() {
        try {
            saveFromForm();
            refresh();
            setStatus("Saved " + normalizedProfile() + ".");
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private ServerRegistration saveFromForm() {
        String profile = normalizedProfile();
        ServerRegistration registration = registrationFromForm(profile);
        ConfigurationDraft draft = new ConfigurationDraft(
                profile,
                imapHostField.getText().trim(),
                parsePort(imapPortField.getText(), "IMAP port"),
                (ConnectionSecurity) imapSecurityField.getSelectedItem(),
                smtpHostField.getText().trim(),
                parsePort(smtpPortField.getText(), "SMTP port"),
                (ConnectionSecurity) smtpSecurityField.getSelectedItem(),
                usernameField.getText().trim(),
                fromAddressField.getText().trim(),
                fromNameField.getText().trim(),
                sentMailboxField.getText().trim()
        );
        new ConfigurationWriter(registration.configFile()).write(draft);
        char[] password = passwordField.getPassword();
        try {
            if (password.length > 0) {
                secretStore.writePassword(profile, password);
                passwordField.setText("");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
        registry.write(registration);
        return registration;
    }

    private void startCurrentProfile() {
        try {
            String profile = normalizedProfile();
            char[] password = passwordField.getPassword();
            try {
                if (password.length == 0 && secretStore.readPassword(profile).isEmpty()) {
                    throw new ConfigurationException("Enter the mailbox password before starting profile " + profile + ".");
                }
            } finally {
                Arrays.fill(password, '\0');
            }
            ServerRegistration registration = saveFromForm();
            long pid = processManager.start(registration);
            refresh();
            selectProfile(registration.profile());
            setStatus("Started " + registration.profile() + " on " + registration.url() + " with PID " + pid + ".");
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void stopSelectedProfile() {
        ServerRegistration registration = selectedOrCurrentRegistration();
        if (registration == null) {
            return;
        }
        try {
            processManager.stop(registration);
            refresh();
            selectProfile(registration.profile());
            setStatus("Stopped " + registration.profile() + ".");
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void copySelectedUrl() {
        ServerRegistration registration = selectedOrCurrentRegistration();
        if (registration != null) {
            copy(registration.url());
            setStatus("Copied " + registration.url() + ".");
        }
    }

    private void copyCurrentUrl() {
        ServerRegistration registration = registrationFromForm(normalizedProfile());
        copy(registration.url());
        setStatus("Copied " + registration.url() + ".");
    }

    private ServerRegistration registrationFromForm(String profile) {
        return new ServerRegistration(
                profile,
                registry.configFile(profile),
                registry.runDir(profile),
                "127.0.0.1",
                parsePort(portField.getText(), "Local MCP port")
        );
    }

    private ServerRegistration selectedOrCurrentRegistration() {
        ServerRegistration selected = selectedRegistration();
        if (selected != null) {
            return selected;
        }
        return registrationFromForm(normalizedProfile());
    }

    private ServerRegistration selectedRegistration() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return tableModel.row(table.convertRowIndexToModel(row));
    }

    private void selectProfile(String profile) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (tableModel.row(row).profile().equals(profile)) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    private String normalizedProfile() {
        return ServerRegistry.registryName(profileField.getText().trim());
    }

    private int parsePort(String raw, String label) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException(raw);
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Invalid " + label + ": " + raw);
        }
    }

    private int firstFreePort(int start) {
        for (int port = start; port <= 65535; port++) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {
                // Try next port.
            }
        }
        return start;
    }

    private void copy(String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    }

    private void setStatus(String value) {
        statusLabel.setText(value);
    }

    private void showError(Throwable throwable) {
        JOptionPane.showMessageDialog(frame, throwable.getMessage(), "OPCoach MCP Mail", JOptionPane.ERROR_MESSAGE);
        setStatus("Error: " + throwable.getMessage());
    }

    private final class ServerTableModel extends AbstractTableModel {

        private final String[] columns = {"Profile", "URL", "Status", "Config"};
        private List<ServerRegistration> rows = new ArrayList<>();

        void setRows(List<ServerRegistration> rows) {
            this.rows = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        ServerRegistration row(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ServerRegistration registration = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> registration.profile();
                case 1 -> registration.url();
                case 2 -> processManager.isRunning(registration) ? "running" : "stopped";
                case 3 -> registration.configFile().toString();
                default -> "";
            };
        }
    }
}
