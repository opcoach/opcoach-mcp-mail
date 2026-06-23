package org.opcoach.mailmcp.config;

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
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ManagerUiApplication {

    private static final Color INDIGO = color("#4B3F72");
    private static final Color INDIGO_SOFT = color("#6C63A6");
    private static final Color INDIGO_LIGHT = color("#E6E4F3");
    private static final Color BLUE = color("#48A5AE");
    private static final Color BLUE_LIGHT = color("#DBECED");
    private static final Color ROSE = color("#E75294");
    private static final Color ROSE_LIGHT = color("#F9DDE9");
    private static final Color YELLOW = color("#FABD43");
    private static final Color GREEN = color("#58B025");
    private static final Color ANTHRACITE = color("#4B4B4D");
    private static final Color TEXT = color("#25252A");
    private static final Color MUTED = color("#6F6F71");
    private static final Color SURFACE = color("#F8F8FC");
    private static final Color CARD = Color.WHITE;
    private static final Color BORDER = color("#DBDBDB");

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
    private JTextField trashMailboxField;
    private JPasswordField passwordField;
    private JLabel statusLabel;

    private ManagerUiApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ManagerUiApplication().show());
    }

    private void show() {
        installLookAndFeel();
        frame = new JFrame("OPCoach MCP Mail");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1040, 720));
        frame.setLayout(new BorderLayout());
        frame.setContentPane(shell());
        refresh();
        newProfile();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        new Timer(3000, _ -> refreshStatuses()).start();
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
        JLabel title = new JLabel("Mail Manager");
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
        actions.add(new GradientButton("Start", GREEN, color("#80C048"), _ -> startCurrentProfile()));
        actions.add(new GradientButton("Stop", ROSE, color("#E975A7"), _ -> stopSelectedProfile()));
        actions.add(new OutlineButton("Copy URL", _ -> copySelectedUrl()));
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
        c.weightx = 0.45;
        root.add(serversCard(), c);

        c.gridx = 1;
        c.weightx = 0.55;
        c.insets = new Insets(0, 0, 0, 0);
        root.add(formCard(), c);
        return root;
    }

    private JPanel serversCard() {
        CardPanel card = new CardPanel();
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        card.add(sectionHeader("Registered servers", "Running status and MCP URLs"), BorderLayout.NORTH);

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
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(230);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedProfile();
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
        row = addField(fields, c, row, "Sent folder", sentMailboxField, "");
        row = addField(fields, c, row, "Trash folder", trashMailboxField, "");
        addField(fields, c, row, "Password", passwordField, "Stored locally in Keychain or Windows DPAPI.");

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
        trashMailboxField.setText("INBOX.Trash");
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
                trashMailboxField.setText(configuration.trashMailbox());
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
                sentMailboxField.getText().trim(),
                trashMailboxField.getText().trim()
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
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
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
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(255, 255, 255, 220));
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
                return statusBadge(value == null ? "" : value.toString(), isSelected);
            }
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            label.setForeground(column == 1 ? INDIGO_SOFT : TEXT);
            label.setBackground(isSelected ? INDIGO_LIGHT : Color.WHITE);
            if (column == 0) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            } else if (column == 3) {
                label.setForeground(MUTED);
                label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
            }
            return label;
        }

        private Component statusBadge(String status, boolean selected) {
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
    }
}
