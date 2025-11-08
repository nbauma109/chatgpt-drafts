package org.jd.gui.util.nexus.ui;

import org.jd.gui.util.nexus.NexusSearch;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * We provide a search panel for NexusSearch implementations.
 *
 * We expose four modes of search:
 *  - Keyword search
 *  - SHA-1 search
 *  - Group, artifact, version search
 *  - Class name search (simple or fully qualified)
 *
 * We execute searches in a background SwingWorker and report progress
 * through a ProgressMonitor and a progress bar. Results are displayed
 * in a table backed by a custom table model.
 */
public final class NexusSearchPanel extends JPanel {

    private final NexusSearch search;

    private final JTabbedPane modeTabs;

    // Keyword tab
    private final JTextField keywordField;

    // SHA-1 tab
    private final JTextField sha1Field;

    // GAV tab
    private final JTextField groupField;
    private final JTextField artifactField;
    private final JTextField versionField;

    // Class tab
    private final JTextField classNameField;
    private final JCheckBox fullyQualifiedCheckBox;

    // Common controls
    private final JSpinner pageSpinner;
    private final JButton searchButton;
    private final JButton cancelButton;
    private final JProgressBar progressBar;

    private final JTable resultTable;
    private final ResultTableModel tableModel;

    private SearchWorker currentWorker;

    public NexusSearchPanel(NexusSearch search) {
        super(new BorderLayout());
        this.search = Objects.requireNonNull(search, "search must not be null");

        // Top: query controls in a tabbed pane
        modeTabs = new JTabbedPane();

        keywordField = new JTextField(30);
        sha1Field = new JTextField(30);
        groupField = new JTextField(20);
        artifactField = new JTextField(20);
        versionField = new JTextField(12);
        classNameField = new JTextField(30);
        fullyQualifiedCheckBox = new JCheckBox("Fully qualified");

        modeTabs.addTab("Keyword", createKeywordPanel());
        modeTabs.addTab("SHA-1", createSha1Panel());
        modeTabs.addTab("Coordinates", createGavPanel());
        modeTabs.addTab("Class", createClassPanel());

        if (!search.supportsClassSearch()) {
            modeTabs.setEnabledAt(3, false);
            modeTabs.setToolTipTextAt(3, "Class search is not supported by this backend");
        }

        JPanel north = new JPanel(new BorderLayout());
        north.add(modeTabs, BorderLayout.CENTER);
        north.add(createControlStrip(), BorderLayout.SOUTH);

        // Center: table with results
        tableModel = new ResultTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setFillsViewportHeight(true);

        JScrollPane scrollPane = new JScrollPane(resultTable);

        add(north, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        add(progressBar, BorderLayout.SOUTH);
    }

    private JPanel createKeywordPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("Keyword:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(keywordField, gbc);

        return panel;
    }

    private JPanel createSha1Panel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("SHA-1:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(sha1Field, gbc);

        return panel;
    }

    private JPanel createGavPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Group:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(groupField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Artifact:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(artifactField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Version (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(versionField, gbc);

        return panel;
    }

    private JPanel createClassPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Class name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(classNameField, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(fullyQualifiedCheckBox, gbc);

        return panel;
    }

    private JPanel createControlStrip() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        pageSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));

        searchButton = new JButton("Search");
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);

        searchButton.addActionListener(e -> startSearch());
        cancelButton.addActionListener(e -> cancelSearch());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Page:"), gbc);

        gbc.gridx = 1;
        panel.add(pageSpinner, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(searchButton, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.0;
        panel.add(cancelButton, gbc);

        return panel;
    }

    private void startSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            return;
        }

        int mode = modeTabs.getSelectedIndex();
        int pageNo = (Integer) pageSpinner.getValue();

        String keyword = null;
        String sha1 = null;
        String groupId = null;
        String artifactId = null;
        String version = null;
        String className = null;
        boolean fullyQualified = false;

        switch (mode) {
            case 0 -> keyword = keywordField.getText().trim();
            case 1 -> sha1 = sha1Field.getText().trim();
            case 2 -> {
                groupId = groupField.getText().trim();
                artifactId = artifactField.getText().trim();
                version = versionField.getText().trim();
                if (version.isEmpty()) {
                    version = null;
                }
            }
            case 3 -> {
                className = classNameField.getText().trim();
                fullyQualified = fullyQualifiedCheckBox.isSelected();
            }
            default -> {
                return;
            }
        }

        SearchRequest request = new SearchRequest(mode, pageNo, keyword, sha1, groupId, artifactId, version, className, fullyQualified);

        currentWorker = new SearchWorker(this, search, request);
        currentWorker.execute();

        searchButton.setEnabled(false);
        cancelButton.setEnabled(true);
    }

    private void cancelSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
    }

    private void onSearchCompleted(NexusSearchResult result, Throwable error) {
        searchButton.setEnabled(true);
        cancelButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("");

        if (error != null) {
            JOptionPane.showMessageDialog(this,
                    error.getClass().getSimpleName() + ": " + error.getMessage(),
                    "Search error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (result == null) {
            tableModel.setArtifacts(List.of());
            return;
        }

        tableModel.setArtifacts(result.artifacts());
    }

    /**
     * We represent an immutable search request snapshot.
     */
    private record SearchRequest(
            int mode,
            int pageNo,
            String keyword,
            String sha1,
            String groupId,
            String artifactId,
            String version,
            String className,
            boolean fullyQualified) {
    }

    /**
     * We encapsulate the background search logic in a SwingWorker.
     * We use the progress property to drive a ProgressMonitor and the
     * embedded progress bar.
     */
    private static final class SearchWorker extends SwingWorker<NexusSearchResult, Void> implements PropertyChangeListener {

        private final NexusSearchPanel panel;
        private final NexusSearch search;
        private final SearchRequest request;

        private final ProgressMonitor monitor;
        private volatile Throwable error;

        SearchWorker(NexusSearchPanel panel, NexusSearch search, SearchRequest request) {
            this.panel = panel;
            this.search = search;
            this.request = request;
            this.monitor = new ProgressMonitor(panel, "Searching remote repository", "", 0, 100);
            this.monitor.setMillisToDecideToPopup(200);
            this.monitor.setMillisToPopup(300);
            addPropertyChangeListener(this);
        }

        @Override
        protected NexusSearchResult doInBackground() {
            try {
                setProgress(5);
                monitor.setNote("Preparing request");
                if (monitor.isCanceled()) {
                    cancel(true);
                    return null;
                }

                NexusSearchResult result;
                setProgress(25);
                monitor.setNote("Executing query");

                if (isCancelled()) {
                    return null;
                }

                switch (request.mode()) {
                    case 0 -> result = search.searchByKeyword(request.keyword(), request.pageNo());
                    case 1 -> result = search.searchBySha1(request.sha1(), request.pageNo());
                    case 2 -> result = search.searchByGav(request.groupId(), request.artifactId(), request.version(), request.pageNo());
                    case 3 -> result = search.searchByClassName(request.className(), request.fullyQualified(), request.pageNo());
                    default -> result = null;
                }

                setProgress(90);
                monitor.setNote("Processing results");

                if (isCancelled()) {
                    return null;
                }

                return result;
            } catch (Throwable t) {
                this.error = t;
                return null;
            } finally {
                setProgress(100);
            }
        }

        @Override
        protected void done() {
            monitor.close();
            try {
                NexusSearchResult result = isCancelled() ? null : get();
                panel.onSearchCompleted(result, error);
            } catch (Exception ex) {
                panel.onSearchCompleted(null, error != null ? error : ex);
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("progress".equals(evt.getPropertyName())) {
                int value = (Integer) evt.getNewValue();
                monitor.setProgress(value);
                panel.progressBar.setValue(value);
                panel.progressBar.setString(value + " %");
                if (monitor.isCanceled()) {
                    cancel(true);
                }
            }
        }
    }

    /**
     * We provide a table model that exposes NexusArtifact properties in columns.
     */
    private static final class ResultTableModel extends AbstractTableModel {

        private static final String[] COLUMN_NAMES = {
                "Group",
                "Artifact",
                "Version",
                "Date",
                "Classifier",
                "Extension",
                "Repository",
                "Artifact link"
        };

        private final List<NexusArtifact> artifacts = new ArrayList<>();

        public void setArtifacts(List<NexusArtifact> newArtifacts) {
            artifacts.clear();
            if (newArtifacts != null) {
                artifacts.addAll(newArtifacts);
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return artifacts.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            NexusArtifact a = artifacts.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> a.groupId();
                case 1 -> a.artifactId();
                case 2 -> a.version();
                case 3 -> {
                    LocalDate date = a.versionDate();
                    yield date != null ? date.toString() : "";
                }
                case 4 -> a.classifier();
                case 5 -> a.extension();
                case 6 -> a.repository();
                case 7 -> a.artifactLink();
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    /**
     * We provide a minimal frame wrapper for manual testing.
     * In a real application we would inject a concrete NexusSearch
     * implementation such as SolrCentralSearchClient or
     * SonatypeCentralSearchClient or a NexusV2Client or NexusV3Client
     * created from configuration.
     */
    public static final class NexusSearchFrame extends JFrame {

        public NexusSearchFrame(NexusSearch search) {
            super("Nexus search");
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setContentPane(new NexusSearchPanel(search));
            pack();
            setLocationRelativeTo(null);
        }

        public static void showWith(NexusSearch search) {
            SwingUtilities.invokeLater(() -> {
                JFrame frame = new NexusSearchFrame(search);
                frame.setVisible(true);
            });
        }
    }

    /**
     * Convenience for manual execution.
     * Replace the placeholder construction by an actual NexusSearch implementation.
     */
    public static void main(String[] args) {
        // Example placeholder, to be replaced with a concrete implementation in our environment:
        // NexusSearch search = new SolrCentralSearchClient(null);
        // NexusSearchFrame.showWith(search);

        JOptionPane.showMessageDialog(null,
                "Please wire an actual NexusSearch implementation in main() before running this class.",
                "NexusSearchPanel demo",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
