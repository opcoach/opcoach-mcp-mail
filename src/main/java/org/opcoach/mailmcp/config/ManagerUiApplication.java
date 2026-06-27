package org.opcoach.mailmcp.config;

import org.opcoach.mailmcp.mail.JakartaImapClient;
import org.opcoach.mailmcp.mail.MailboxInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ManagerUiApplication {

    private static final Color INDIGO = color("#4B3F72");
    private static final Color INDIGO_SOFT = color("#6C63A6");
    private static final Color INDIGO_LIGHT = color("#E6E4F3");
    private static final Color BLUE = color("#48A5AE");
    private static final Color BLUE_LIGHT = color("#DBECED");
    private static final Color ROSE = color("#E75294");
    private static final Color ROSE_LIGHT = color("#F9DDE9");
    private static final Color YELLOW_LIGHT = color("#FFF1CF");
    private static final Color GREEN = color("#58B025");
    private static final Color ANTHRACITE = color("#4B4B4D");
    private static final Color TEXT = color("#25252A");
    private static final Color MUTED = color("#6F6F71");
    private static final Color SURFACE = color("#F8F8FC");
    private static final Color CARD = Color.WHITE;
    private static final Color BORDER = color("#DBDBDB");
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private final ServerRegistry registry = ServerRegistry.defaultRegistry();
    private final ServerProcessManager processManager = ServerProcessManager.currentApplication();
    private final SecretStore secretStore = LocalSecretStore.system();
    private final ServerTableModel tableModel = new ServerTableModel();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HEALTH_TIMEOUT)
            .build();
    private final ExecutorService healthExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "opcoach-mcp-mail-health");
        thread.setDaemon(true);
        return thread;
    });
    private final java.util.Map<String, HealthStatus> healthStatuses = new ConcurrentHashMap<>();
    private final Set<String> healthChecksInFlight = ConcurrentHashMap.newKeySet();

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
    private JTextField replyToAddressField;
    private JTextField sentMailboxField;
    private JTextField trashMailboxField;
    private JPasswordField passwordField;
    private JLabel statusLabel;
    private JButton startButton;
    private JButton stopButton;
    private JButton deleteButton;
    private JButton copyUrlButton;

    private ManagerUiApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ManagerUiApplication().show());
    }

    private void show() {
        installLookAndFeel();
        frame = new JFrame("MCP Mail Local Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1040, 720));
        frame.setLayout(new BorderLayout());
        frame.setContentPane(shell());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                healthExecutor.shutdownNow();
            }
        });
        refresh();
        newProfile();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        new Timer(30000, _ -> refreshStatuses()).start();
    }

    private JPanel shell() {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(SURFACE);
        shell.add(hero(), BorderLayout.NORTH);
        shell.add(content(), BorderLayout.CENTER);
        statusLabel = new JLabel("Ready.");
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 13f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 24, 16, 24));
        shell.add(statusLabel, BorderLayout.SOUTH);
        return shell;
    }

    private JPanel hero() {
        GradientPanel hero = new GradientPanel(INDIGO, INDIGO_SOFT);
        hero.setLayout(new BorderLayout(20, 0));
        hero.setBorder(BorderFactory.createEmptyBorder(26, 28, 24, 28));

        JPanel copy = transparentPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 0);
        JLabel eyebrow = new JLabel("OPCoach local-first MCP");
        eyebrow.setForeground(color("#E6E4F3"));
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 13f));
        copy.add(eyebrow, c);

        c.gridy++;
        JLabel title = new JLabel("MCP Mail Local Manager");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 34f));
        copy.add(title, c);

        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        JLabel subtitle = new JLabel("Configure mailboxes, start local MCP servers, copy the URL.");
        subtitle.setForeground(color("#F2F1FA"));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 15f));
        copy.add(subtitle, c);

        JPanel actions = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        actions.add(new GradientButton("New", BLUE, color("#70B6BD"), _ -> newProfile()));
        actions.add(new GradientButton("Save", INDIGO_SOFT, color("#8F88C7"), _ -> saveCurrentProfile()));
        startButton = new GradientButton("Start", GREEN, color("#80C048"), _ -> startCurrentProfile());
        stopButton = new GradientButton("Stop", ROSE, color("#E975A7"), _ -> stopSelectedProfile());
        deleteButton = new GradientButton("Delete", color("#B43A67"), ROSE, _ -> deleteSelectedProfile());
        copyUrlButton = new OutlineButton("Copy URL", _ -> copySelectedUrl());
        actions.add(startButton);
        actions.add(stopButton);
        actions.add(deleteButton);
        actions.add(copyUrlButton);
        actions.add(new OutlineButton("Refresh", _ -> refresh()));

        hero.add(copy, BorderLayout.CENTER);
        hero.add(actions, BorderLayout.EAST);
        return hero;
    }

    private JPanel content() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 16);

        c.gridx = 0;
        c.weightx = 0.60;
        root.add(serversCard(), c);

        c.gridx = 1;
        c.weightx = 0.40;
        c.insets = new Insets(0, 0, 0, 0);
        root.add(formCard(), c);
        return root;
    }

    private JPanel serversCard() {
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        card.add(sectionHeader("Registered servers", "Running and mailbox health"), BorderLayout.NORTH);

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(42);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setFont(table.getFont().deriveFont(Font.PLAIN, 13f));
        table.setSelectionBackground(INDIGO_LIGHT);
        table.setSelectionForeground(TEXT);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setForeground(ANTHRACITE);
        table.getTableHeader().setBackground(Color.WHITE);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        table.setDefaultRenderer(Object.class, new ModernTableRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(210);
        table.getColumnModel().getColumn(2).setPreferredWidth(85);
        table.getColumnModel().getColumn(3).setPreferredWidth(170);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedProfile();
                updateActionButtons();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 1) {
                    showHealthDetailsAt(event);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new RoundedBorder(BORDER, 14, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        card.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        footer.setOpaque(false);
        footer.add(new ColorDot(GREEN));
        footer.add(smallText("running"));
        footer.add(new ColorDot(ROSE));
        footer.add(smallText("stopped"));
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel formCard() {
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        card.add(sectionHeader("Profile configuration", "Mailbox settings and local MCP port"), BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 8, 6, 8);

        profileField = styledTextField();
        portField = styledTextField();
        imapHostField = styledTextField();
        imapPortField = styledTextField("993");
        imapSecurityField = styledCombo();
        smtpHostField = styledTextField();
        smtpPortField = styledTextField("465");
        smtpSecurityField = styledCombo();
        usernameField = styledTextField();
        fromAddressField = styledTextField();
        fromNameField = styledTextField();
        replyToAddressField = styledTextField();
        sentMailboxField = styledTextField("INBOX.Sent");
        trashMailboxField = styledTextField("INBOX.Trash");
        passwordField = new JPasswordField();
        styleTextComponent(passwordField);

        int row = 0;
        row = addGroupTitle(fields, c, row, "Server");
        row = addField(fields, c, row, "Profile", profileField, "Short name used by Codex and logs.");
        row = addField(fields, c, row, "Local MCP port", portField, "Usually 8095, 8096, 8097...");
        row = addGroupTitle(fields, c, row, "Incoming mail");
        row = addField(fields, c, row, "IMAP host", imapHostField, "Example: imap.example.com");
        row = addField(fields, c, row, "IMAP port", imapPortField, "993 for SSL/TLS.");
        row = addField(fields, c, row, "IMAP security", imapSecurityField, "");
        row = addGroupTitle(fields, c, row, "Outgoing mail");
        row = addField(fields, c, row, "SMTP host", smtpHostField, "Example: smtp.example.com");
        row = addField(fields, c, row, "SMTP port", smtpPortField, "465 for SSL/TLS, 587 for STARTTLS.");
        row = addField(fields, c, row, "SMTP security", smtpSecurityField, "");
        row = addGroupTitle(fields, c, row, "Identity");
        row = addField(fields, c, row, "Email username", usernameField, "");
        row = addField(fields, c, row, "Sender address", fromAddressField, "");
        row = addField(fields, c, row, "Sender name", fromNameField, "");
        row = addField(fields, c, row, "Reply-To address", replyToAddressField, "Optional.");
        row = addField(fields, c, row, "Sent folder", sentMailboxField, "");
        row = addField(fields, c, row, "Trash folder", trashMailboxField, "");
        addField(fields, c, row, "Password", passwordField, "Stored locally when supported; otherwise used for this start.");

        JScrollPane scroll = new JScrollPane(fields);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        card.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(new OutlineButton("Copy this URL", _ -> copyCurrentUrl()));
        actions.add(new GradientButton("Save and start", INDIGO, ROSE, _ -> startCurrentProfile()));
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel sectionHeader(String title, String subtitle) {
        JPanel panel = transparentPanel(new BorderLayout());
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(MUTED);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 13f));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(subtitleLabel, BorderLayout.SOUTH);
        return panel;
    }

    private int addGroupTitle(JPanel panel, GridBagConstraints c, int row, String title) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1;
        c.insets = new Insets(row == 0 ? 2 : 16, 8, 2, 8);
        JLabel label = new JLabel(title.toUpperCase(java.util.Locale.ROOT));
        label.setForeground(INDIGO);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        panel.add(label, c);
        c.gridwidth = 1;
        return row + 1;
    }

    private int addField(JPanel panel, GridBagConstraints c, int row, String label, JComponent field, String hint) {
        c.gridy = row;
        c.insets = new Insets(5, 8, 5, 8);
        c.gridx = 0;
        c.weightx = 0;
        c.anchor = GridBagConstraints.WEST;
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(ANTHRACITE);
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(labelComponent, c);

        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);

        c.gridx = 2;
        c.weightx = 0.2;
        JLabel hintLabel = new JLabel(hint);
        hintLabel.setForeground(color("#939394"));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(hintLabel, c);
        return row + 1;
    }

    private void refresh() {
        String selectedProfile = selectedProfileName();
        List<ServerRegistration> registrations = registry.list();
        tableModel.setRows(registrations);
        queueHealthChecks(registrations, "");
        restoreSelection(selectedProfile);
        updateActionButtons();
    }

    private void refreshStatuses() {
        String selectedProfile = selectedProfileName();
        queueHealthChecks(tableModel.rows(), "");
        tableModel.fireTableDataChanged();
        restoreSelection(selectedProfile);
        updateActionButtons();
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
        replyToAddressField.setText("");
        sentMailboxField.setText("INBOX.Sent");
        trashMailboxField.setText("INBOX.Trash");
        passwordField.setText("");
        table.clearSelection();
        updateActionButtons();
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
                replyToAddressField.setText(configuration.replyToAddress());
                sentMailboxField.setText(configuration.sentMailbox());
                trashMailboxField.setText(configuration.trashMailbox());
                setStatus("Loaded " + registration.profile() + ".");
            } catch (ConfigurationException exception) {
                showError(exception);
            }
        }
    }

    private void saveCurrentProfile() {
        try {
            ServerRegistration registration = saveFromForm();
            refresh();
            selectProfile(registration.profile());
            queueHealthCheck(registration, "");
            setStatus("Saved " + normalizedProfile() + ".");
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private ServerRegistration saveFromForm() {
        String profile = normalizedProfile();
        ServerRegistration registration = registrationFromForm(profile);
        validatePortAvailability(registration);
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
                replyToAddressField.getText().trim(),
                sentMailboxField.getText().trim(),
                trashMailboxField.getText().trim()
        );
        new ConfigurationWriter(registration.configFile()).write(draft);
        char[] password = passwordField.getPassword();
        try {
            if (password.length > 0) {
                if (secretStore.supportsDurableStorage()) {
                    secretStore.writePassword(profile, password);
                }
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
            String transientPassword;
            try {
                if (password.length == 0 && secretStore.readPassword(profile).isEmpty()) {
                    throw new ConfigurationException("Enter the mailbox password before starting profile " + profile + ".");
                }
                transientPassword = password.length > 0 && !secretStore.supportsDurableStorage()
                        ? new String(password)
                        : "";
            } finally {
                Arrays.fill(password, '\0');
            }
            ServerRegistration registration = saveFromForm();
            long pid = processManager.start(registration, transientPassword);
            refresh();
            selectProfile(registration.profile());
            queueHealthCheck(registration, transientPassword);
            updateActionButtons();
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
            healthStatuses.put(healthKey(registration), HealthStatus.stopped());
            refresh();
            selectProfile(registration.profile());
            updateActionButtons();
            setStatus("Stopped " + registration.profile() + ".");
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void deleteSelectedProfile() {
        ServerRegistration registration = selectedRegistration();
        if (registration == null) {
            setStatus("Select a registered server before deleting it.");
            return;
        }
        String message = """
                Delete profile "%s"?

                The local MCP server will be stopped if it is running.
                The manager registration and local configuration file will be deleted.
                The stored password will be removed when the platform supports it.
                Mailbox messages are never deleted by this action.
                """.formatted(registration.profile());
        int choice = JOptionPane.showConfirmDialog(
                frame,
                message,
                "Delete server",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            setStatus("Delete cancelled.");
            return;
        }
        try {
            processManager.stop(registration);
            registry.delete(registration);
            healthStatuses.remove(healthKey(registration));
            boolean passwordDeleted = secretStore.deletePassword(registration.profile());
            refresh();
            newProfile();
            updateActionButtons();
            String secretStatus = passwordDeleted ? " Stored password was removed." : "";
            setStatus("Deleted " + registration.profile() + ". Mailbox messages were not modified." + secretStatus);
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

    private void validatePortAvailability(ServerRegistration registration) {
        for (ServerRegistration existing : registry.list()) {
            boolean sameProfile = existing.profile().equals(registration.profile());
            if (!sameProfile && existing.port() == registration.port()) {
                throw new ConfigurationException("Port " + registration.port() + " is already used by profile " + existing.profile() + ".");
            }
        }
        ServerRegistration selected = selectedRegistration();
        boolean sameRunningServer = selected != null
                && selected.profile().equals(registration.profile())
                && selected.port() == registration.port()
                && processManager.isRunning(selected);
        if (!sameRunningServer && !isPortFree(registration.port())) {
            throw new ConfigurationException("Port " + registration.port() + " is already in use by another process.");
        }
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

    private void updateActionButtons() {
        if (startButton == null || stopButton == null || deleteButton == null || copyUrlButton == null) {
            return;
        }
        ServerRegistration selected = selectedRegistration();
        boolean hasSelection = selected != null;
        boolean running = hasSelection && processManager.isRunning(selected);
        startButton.setEnabled(hasSelection && !running);
        stopButton.setEnabled(hasSelection && running);
        deleteButton.setEnabled(hasSelection);
        copyUrlButton.setEnabled(hasSelection);
    }

    private void showHealthDetailsAt(MouseEvent event) {
        int row = table.rowAtPoint(event.getPoint());
        int column = table.columnAtPoint(event.getPoint());
        if (row < 0 || column < 0 || table.convertColumnIndexToModel(column) != 3) {
            return;
        }
        ServerRegistration registration = tableModel.row(table.convertRowIndexToModel(row));
        HealthStatus health = healthStatuses.getOrDefault(healthKey(registration), HealthStatus.notChecked());
        if (!health.hasDetails()) {
            return;
        }
        showHealthDetails(registration, health);
    }

    private void showHealthDetails(ServerRegistration registration, HealthStatus health) {
        JTextArea details = new JTextArea(health.diagnosticText(registration));
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(details);
        scroll.setPreferredSize(new Dimension(720, 420));

        Object[] options = {"Copy details", "Close"};
        int choice = JOptionPane.showOptionDialog(
                frame,
                scroll,
                "Mail check details",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]
        );
        if (choice == 0) {
            copy(details.getText());
            setStatus("Copied mail check details for " + registration.profile() + ".");
        }
    }

    private void selectProfile(String profile) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (tableModel.row(row).profile().equals(profile)) {
                int viewRow = table.convertRowIndexToView(row);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                }
                updateActionButtons();
                return;
            }
        }
    }

    private String selectedProfileName() {
        ServerRegistration registration = selectedRegistration();
        return registration == null ? "" : registration.profile();
    }

    private void restoreSelection(String profile) {
        if (profile != null && !profile.isBlank()) {
            selectProfile(profile);
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
        Set<Integer> registeredPorts = new HashSet<>();
        for (ServerRegistration registration : registry.list()) {
            registeredPorts.add(registration.port());
        }
        for (int port = start; port <= 65535; port++) {
            if (registeredPorts.contains(port)) {
                continue;
            }
            if (isPortFree(port)) {
                return port;
            }
        }
        return start;
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void queueHealthChecks(List<ServerRegistration> registrations, String passwordOverride) {
        for (ServerRegistration registration : registrations) {
            queueHealthCheck(registration, passwordOverride);
        }
    }

    private void queueHealthCheck(ServerRegistration registration, String passwordOverride) {
        String key = healthKey(registration);
        if (!healthChecksInFlight.add(key)) {
            return;
        }
        healthStatuses.putIfAbsent(key, HealthStatus.checking());
        healthExecutor.execute(() -> {
            HealthStatus status;
            try {
                status = checkHealth(registration, passwordOverride);
            } catch (RuntimeException exception) {
                status = HealthStatus.error(errorLabel(exception), stackTrace(exception), resolutionFor(exception));
            } finally {
                healthChecksInFlight.remove(key);
            }
            healthStatuses.put(key, status);
            SwingUtilities.invokeLater(() -> {
                String selectedProfile = selectedProfileName();
                tableModel.fireTableDataChanged();
                restoreSelection(selectedProfile);
                updateActionButtons();
            });
        });
    }

    private HealthStatus checkHealth(ServerRegistration registration, String passwordOverride) {
        boolean running = processManager.isRunning(registration);
        if (running && !httpHealthOk(registration)) {
            return HealthStatus.warning(
                    "HTTP unavailable",
                    "GET " + healthUrl(registration) + " did not return a 2xx response within " + HEALTH_TIMEOUT.toSeconds() + " seconds.",
                    "Check that the local MCP process is still running and that no other process is bound to this port."
            );
        }
        if (!Files.exists(registration.configFile())) {
            return HealthStatus.warning(
                    "Missing config",
                    "Configuration file not found: " + registration.configFile(),
                    "Save the profile again from the manager."
            );
        }
        MailConfiguration configuration = new ConfigurationLoader(registration.configFile()).load(registration.profile());
        String password;
        try {
            password = passwordOverride == null || passwordOverride.isBlank()
                    ? secretStore.readPassword(configuration.profile()).orElse("")
                    : passwordOverride;
        } catch (ConfigurationException exception) {
            return HealthStatus.warning("Secret locked", stackTrace(exception), "Unlock the local secret store or start the server with the vault password.");
        }
        if (password.isBlank()) {
            return HealthStatus.warning(
                    running ? "MCP ok, secret locked" : "Secret missing",
                    "No password is available for profile " + configuration.profile() + ".",
                    "Enter the mailbox password in the manager, or configure the platform local secret store."
            );
        }
        List<MailboxInfo> mailboxes;
        try {
            mailboxes = new JakartaImapClient(configuration, password).listMailboxes(false);
        } catch (RuntimeException exception) {
            return HealthStatus.error(errorLabel(exception), stackTrace(exception), resolutionFor(exception));
        }
        MailboxInfo inbox = findMailbox(mailboxes, "INBOX");
        if (inbox == null) {
            return HealthStatus.warning(
                    "Missing INBOX",
                    "The IMAP connection succeeded, but no folder named INBOX was returned.\n\nAvailable folders:\n" + mailboxList(mailboxes),
                    "Check the mailbox provider folder naming and IMAP namespace."
            );
        }
        List<String> missing = new ArrayList<>();
        if (findMailbox(mailboxes, configuration.sentMailbox()) == null) {
            missing.add(configuration.sentMailbox());
        }
        if (findMailbox(mailboxes, configuration.trashMailbox()) == null) {
            missing.add(configuration.trashMailbox());
        }
        if (!missing.isEmpty()) {
            String missingLabel = missing.size() == 1 ? missing.getFirst() : missing.getFirst() + " +" + (missing.size() - 1);
            return HealthStatus.warning(
                    "Missing " + missingLabel,
                    "Missing configured folder(s): " + String.join(", ", missing) + "\n\nAvailable folders:\n" + mailboxList(mailboxes),
                    "Open the mailbox folder list and update the Sent/Trash folder names in the profile configuration."
            );
        }
        return HealthStatus.ok("INBOX " + inbox.messageCount());
    }

    private boolean httpHealthOk(ServerRegistration registration) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl(registration)))
                    .timeout(HEALTH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String healthUrl(ServerRegistration registration) {
        return "http://" + registration.host() + ":" + registration.port() + "/health";
    }

    private static MailboxInfo findMailbox(List<MailboxInfo> mailboxes, String fullName) {
        for (MailboxInfo mailbox : mailboxes) {
            if (mailbox.fullName().equalsIgnoreCase(fullName)) {
                return mailbox;
            }
        }
        return null;
    }

    private static String mailboxList(List<MailboxInfo> mailboxes) {
        if (mailboxes.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (MailboxInfo mailbox : mailboxes) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(mailbox.fullName())
                    .append(" (")
                    .append(mailbox.messageCount())
                    .append(")");
        }
        return builder.toString();
    }

    private static String errorLabel(Throwable throwable) {
        String text = throwableText(throwable);
        if (containsAny(text, "auth", "login", "credential", "password", "invalid credentials")) {
            return "Error: Authentication";
        }
        if (containsAny(text, "timeout", "timed out", "connection", "unknown host", "network", "refused")) {
            return "Error: Network";
        }
        if (containsAny(text, "ssl", "tls", "certificate", "handshake")) {
            return "Error: TLS";
        }
        if (containsAny(text, "configuration", "missing", "invalid")) {
            return "Error: Configuration";
        }
        return "Error: IMAP";
    }

    private static String resolutionFor(Throwable throwable) {
        String text = throwableText(throwable);
        if (containsAny(text, "auth", "login", "credential", "password", "invalid credentials")) {
            return "Check the email username and app password. If the provider requires app passwords, generate a new one and save it again in the manager.";
        }
        if (containsAny(text, "timeout", "timed out", "connection", "unknown host", "network", "refused")) {
            return "Check the IMAP host, port, network access, firewall, and whether the provider allows IMAP connections from this machine.";
        }
        if (containsAny(text, "ssl", "tls", "certificate", "handshake")) {
            return "Check the selected security mode, port, and provider TLS certificate requirements.";
        }
        if (containsAny(text, "configuration", "missing", "invalid")) {
            return "Review the profile configuration and save it again.";
        }
        return "Review the complete exception below, then check the IMAP provider settings and mailbox folder names.";
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String throwableText(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getClass().getName() != null) {
                builder.append(current.getClass().getName()).append(' ');
            }
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String healthKey(ServerRegistration registration) {
        return registration.profile() + "@" + registration.host() + ":" + registration.port();
    }

    private void copy(String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    }

    private void setStatus(String value) {
        statusLabel.setText(value);
    }

    private void showError(Throwable throwable) {
        JOptionPane.showMessageDialog(frame, throwable.getMessage(), "MCP Mail Local Manager", JOptionPane.ERROR_MESSAGE);
        setStatus("Error: " + throwable.getMessage());
    }

    private static JTextField styledTextField() {
        return styledTextField("");
    }

    private static JTextField styledTextField(String value) {
        JTextField field = new JTextField(value);
        styleTextComponent(field);
        return field;
    }

    private static JComboBox<ConnectionSecurity> styledCombo() {
        JComboBox<ConnectionSecurity> combo = new JComboBox<>(ConnectionSecurity.values());
        combo.setBackground(Color.WHITE);
        combo.setForeground(TEXT);
        combo.setBorder(new RoundedBorder(color("#CFCDE1"), 12, 1));
        combo.setFont(combo.getFont().deriveFont(Font.PLAIN, 13f));
        return combo;
    }

    private static void styleTextComponent(JTextField field) {
        field.setOpaque(false);
        field.setForeground(TEXT);
        field.setCaretColor(INDIGO);
        field.setFont(field.getFont().deriveFont(Font.PLAIN, 13f));
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(color("#CFCDE1"), 12, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
    }

    private static JPanel transparentPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel smallText(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        return label;
    }

    private static Color color(String hex) {
        return Color.decode(hex);
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
            UIManager.put("ScrollBar.width", 12);
        } catch (Exception ignored) {
            // Keep the default look and feel if the platform one is unavailable.
        }
    }

    private final class ServerTableModel extends AbstractTableModel {

        private final String[] columns = {"Profile", "URL", "Run", "Mail check"};
        private List<ServerRegistration> rows = new ArrayList<>();

        void setRows(List<ServerRegistration> rows) {
            this.rows = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        List<ServerRegistration> rows() {
            return List.copyOf(rows);
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
                case 3 -> healthStatuses.getOrDefault(healthKey(registration), HealthStatus.notChecked()).label();
                default -> "";
            };
        }
    }

    private record HealthStatus(String label, HealthSeverity severity, String detail, String suggestion) {

        static HealthStatus notChecked() {
            return new HealthStatus("not checked", HealthSeverity.NEUTRAL, "", "");
        }

        static HealthStatus checking() {
            return new HealthStatus("checking...", HealthSeverity.NEUTRAL, "", "");
        }

        static HealthStatus stopped() {
            return new HealthStatus("not running", HealthSeverity.STOPPED, "", "");
        }

        static HealthStatus ok(String label) {
            return new HealthStatus(label, HealthSeverity.OK, "", "");
        }

        static HealthStatus warning(String label) {
            return warning(label, "", "");
        }

        static HealthStatus warning(String label, String detail, String suggestion) {
            return new HealthStatus(label, HealthSeverity.WARNING, detail, suggestion);
        }

        static HealthStatus error(String label) {
            return error(label, "", "");
        }

        static HealthStatus error(String label, String detail, String suggestion) {
            return new HealthStatus(label, HealthSeverity.ERROR, detail, suggestion);
        }

        boolean hasDetails() {
            return (detail != null && !detail.isBlank()) || (suggestion != null && !suggestion.isBlank());
        }

        String diagnosticText(ServerRegistration registration) {
            return """
                    Profile: %s
                    URL: %s
                    Status: %s

                    Suggested resolution:
                    %s

                    Details:
                    %s
                    """.formatted(
                    registration.profile(),
                    registration.url(),
                    label,
                    blankToDefault(suggestion, "No automatic suggestion is available."),
                    blankToDefault(detail, "No diagnostic details are available.")
            );
        }
    }

    private enum HealthSeverity {
        OK,
        WARNING,
        ERROR,
        STOPPED,
        NEUTRAL
    }

    private static final class GradientPanel extends JPanel {

        private final Color start;
        private final Color end;

        private GradientPanel(Color start, Color end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 255, 255, 28));
            g.fillOval(getWidth() - 190, -120, 310, 310);
            g.setColor(new Color(250, 189, 67, 42));
            g.fillOval(getWidth() - 350, 70, 160, 160);
            g.dispose();
        }
    }

    private static final class CardPanel extends JPanel {

        private CardPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(75, 63, 114, 22));
            g.fillRoundRect(3, 5, getWidth() - 6, getHeight() - 6, 24, 24);
            g.setColor(CARD);
            g.fillRoundRect(0, 0, getWidth() - 6, getHeight() - 8, 24, 24);
            g.setColor(color("#ECEAF7"));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(0, 0, getWidth() - 7, getHeight() - 9, 24, 24);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class GradientButton extends JButton {

        private final Color start;
        private final Color end;

        GradientButton(String text, Color start, Color end, java.awt.event.ActionListener listener) {
            super(text);
            this.start = start;
            this.end = end;
            addActionListener(listener);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            setForeground(enabled ? Color.WHITE : color("#909096"));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color paintStart = isEnabled() ? start : color("#E4E4EA");
            Color paintEnd = isEnabled() ? end : color("#D8D8E0");
            g.setPaint(new GradientPaint(0, 0, paintStart, getWidth(), getHeight(), paintEnd));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g.setColor(new Color(255, 255, 255, 38));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class OutlineButton extends GradientButton {

        OutlineButton(String text, java.awt.event.ActionListener listener) {
            super(text, Color.WHITE, Color.WHITE, listener);
            setForeground(INDIGO);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setForeground(enabled ? INDIGO : color("#A2A2AA"));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isEnabled() ? new Color(255, 255, 255, 220) : color("#ECECF1"));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g.setColor(new Color(255, 255, 255, 150));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedBorder extends AbstractBorder {

        private final Color color;
        private final int radius;
        private final int thickness;

        private RoundedBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(thickness, thickness, thickness, thickness);
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(thickness));
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.dispose();
        }
    }

    private static final class ColorDot extends JComponent {

        private final Color color;

        private ColorDot(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(10, 10));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(1, 1, 8, 8);
            g.dispose();
        }
    }

    private final class ModernTableRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            if (column == 2) {
                return runBadge(value == null ? "" : value.toString(), isSelected);
            }
            if (column == 3) {
                int modelRow = table.convertRowIndexToModel(row);
                ServerRegistration registration = tableModel.row(modelRow);
                HealthStatus health = healthStatuses.getOrDefault(healthKey(registration), HealthStatus.notChecked());
                return healthBadge(health, isSelected);
            }
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            label.setForeground(column == 1 ? INDIGO_SOFT : TEXT);
            label.setBackground(isSelected ? INDIGO_LIGHT : Color.WHITE);
            if (column == 0) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            return label;
        }

        private Component runBadge(String status, boolean selected) {
            JLabel label = new JLabel(status, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color background = "running".equals(status) ? color("#EAF7E4") : ROSE_LIGHT;
                    g.setColor(selected ? INDIGO_LIGHT : Color.WHITE);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(background);
                    FontMetrics metrics = g.getFontMetrics(getFont());
                    int width = Math.max(72, metrics.stringWidth(status) + 24);
                    int x = (getWidth() - width) / 2;
                    int y = (getHeight() - 24) / 2;
                    g.fillRoundRect(x, y, width, 24, 16, 16);
                    g.dispose();
                    super.paintComponent(graphics);
                }
            };
            label.setOpaque(false);
            label.setForeground("running".equals(status) ? GREEN : ROSE);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
            label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return label;
        }

        private Component healthBadge(HealthStatus health, boolean selected) {
            JLabel label = new JLabel(health.label(), SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(selected ? INDIGO_LIGHT : Color.WHITE);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(healthBackground(health.severity()));
                    FontMetrics metrics = g.getFontMetrics(getFont());
                    int width = Math.min(getWidth() - 8, Math.max(92, metrics.stringWidth(health.label()) + 24));
                    int x = (getWidth() - width) / 2;
                    int y = (getHeight() - 24) / 2;
                    g.fillRoundRect(x, y, width, 24, 16, 16);
                    g.dispose();
                    super.paintComponent(graphics);
                }
            };
            label.setOpaque(false);
            label.setForeground(healthForeground(health.severity()));
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
            label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            label.setToolTipText(health.hasDetails() ? "Click to show diagnostic details." : health.label());
            return label;
        }

        private Color healthBackground(HealthSeverity severity) {
            return switch (severity) {
                case OK -> color("#EAF7E4");
                case WARNING -> YELLOW_LIGHT;
                case ERROR -> ROSE_LIGHT;
                case STOPPED, NEUTRAL -> color("#F0F0F4");
            };
        }

        private Color healthForeground(HealthSeverity severity) {
            return switch (severity) {
                case OK -> GREEN;
                case WARNING -> color("#B36B00");
                case ERROR -> ROSE;
                case STOPPED, NEUTRAL -> MUTED;
            };
        }
    }
}
