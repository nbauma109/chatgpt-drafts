package org.jd.gui.util.maven.central.helper;

import org.jd.gui.api.API;
import org.jd.gui.util.NexusConfigHelper;
import org.jd.gui.util.ProxyConfigHelper;
import org.jd.gui.util.maven.central.helper.model.response.docs.Docs;
import org.jd.gui.util.nexus.NexusConfig;
import org.jd.gui.util.nexus.NexusSearch;
import org.jd.gui.util.nexus.NexusSearchFactory;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;
import org.jd.gui.util.maven.central.helper.model.response.Response;
import org.jd.gui.util.maven.central.helper.model.ResponseRoot;
import org.jd.gui.util.maven.central.helper.ProxyConfig;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.HighlighterFactory;
import org.oxbow.swingbits.list.CheckListRenderer;
import org.oxbow.swingbits.table.filter.TableRowFilterSupport;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 * in a table backed by a custom table model. Snippets for several build
 * tools are shown for the selected row in RSyntaxTextArea tabs.
 */
public final class NexusSearchPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_PAGES = 50;

    private final transient NexusSearch search;

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

    // Controls
    private JButton searchButton;
    private JButton cancelButton;
    private final JProgressBar progressBar;

    private final JXTable resultTable;
    private final ResultTableModel tableModel;

    private final JTabbedPane snippetTabs;
    private final RSyntaxTextArea mavenArea;
    private final RSyntaxTextArea gradleArea;
    private final RSyntaxTextArea ivyArea;
    private final RSyntaxTextArea sbtArea;
    private final RSyntaxTextArea leinArea;
    private final RSyntaxTextArea grapeArea;
    private final RSyntaxTextArea buildrArea;
    private final RSyntaxTextArea bldArea;

    private transient SearchWorker currentWorker;

    public NexusSearchPanel(API api) {
        super(new BorderLayout());

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

        ProxyConfig proxyConfig = ProxyConfigHelper.fromPreferences(api.getPreferences(), this);
        NexusConfig nexusConfig = NexusConfigHelper.fromPreferences(api.getPreferences(), this);
        search = NexusSearchFactory.create(nexusConfig, proxyConfig);
        if (!search.supportsClassSearch()) {
            modeTabs.setEnabledAt(3, false);
            modeTabs.setToolTipTextAt(3, "Class search is not supported by this backend");
        }

        JPanel north = new JPanel(new BorderLayout());
        north.add(modeTabs, BorderLayout.CENTER);
        north.add(createControlStrip(), BorderLayout.SOUTH);

        tableModel = new ResultTableModel();
        resultTable = new JXTable(tableModel);
        resultTable.setFillsViewportHeight(true);
        resultTable.setColumnControlVisible(true);
        resultTable.setHighlighters(HighlighterFactory.createSimpleStriping());
        TableRowFilterSupport.forTable(resultTable)
                .actions(true)
                .searchable(true)
                .checkListRenderer(new CheckListRenderer())
                .apply();

        resultTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                int viewRow = resultTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = resultTable.convertRowIndexToModel(viewRow);
                    NexusArtifact artifact = tableModel.getArtifactAt(modelRow);
                    updateSnippets(artifact);
                } else {
                    updateSnippets(null);
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(resultTable);

        snippetTabs = new JTabbedPane();
        mavenArea = createReadOnlyEditor();
        gradleArea = createReadOnlyEditor();
        ivyArea = createReadOnlyEditor();
        sbtArea = createReadOnlyEditor();
        leinArea = createReadOnlyEditor();
        grapeArea = createReadOnlyEditor();
        buildrArea = createReadOnlyEditor();
        bldArea = createReadOnlyEditor();

        snippetTabs.addTab("Maven", new RTextScrollPane(mavenArea));
        snippetTabs.addTab("Gradle", new RTextScrollPane(gradleArea));
        snippetTabs.addTab("Ivy", new RTextScrollPane(ivyArea));
        snippetTabs.addTab("SBT", new RTextScrollPane(sbtArea));
        snippetTabs.addTab("Leiningen", new RTextScrollPane(leinArea));
        snippetTabs.addTab("Grape", new RTextScrollPane(grapeArea));
        snippetTabs.addTab("Buildr", new RTextScrollPane(buildrArea));
        snippetTabs.addTab("bld", new RTextScrollPane(bldArea));

        javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT,
                tableScrollPane,
                snippetTabs
        );
        splitPane.setResizeWeight(0.7);

        add(north, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        add(progressBar, BorderLayout.SOUTH);
    }

    private static RSyntaxTextArea createReadOnlyEditor() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setEditable(false);
        area.setCodeFoldingEnabled(false);
        return area;
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
        panel.add(searchButton, gbc);

        gbc.gridx = 1;
        panel.add(cancelButton, gbc);

        return panel;
    }

    private void startSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            return;
        }

        int mode = modeTabs.getSelectedIndex();

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

        SearchRequest request = new SearchRequest(mode, keyword, sha1, groupId, artifactId, version, className, fullyQualified);

        currentWorker = new SearchWorker(this, search, request);
        currentWorker.execute();

        searchButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setString("Starting...");
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
            tableModel.setArtifacts(List.of());
            updateSnippets(null);
            return;
        }

        if (result == null || result.artifacts() == null) {
            tableModel.setArtifacts(List.of());
            updateSnippets(null);
            return;
        }

        tableModel.setArtifacts(result.artifacts());

        if (!result.artifacts().isEmpty()) {
            resultTable.getSelectionModel().setSelectionInterval(0, 0);
            updateSnippets(result.artifacts().get(0));
        } else {
            updateSnippets(null);
        }
    }

    /**
     * We represent an immutable search request snapshot.
     */
    private record SearchRequest(
            int mode,
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
     * We page through results until there are no more results, a maximum
     * page count is reached, or the user cancels. SHA-1 search is treated
     * as a single page operation.
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

                // SHA-1: we perform a single request and do not page
                if (request.mode() == 1) {
                    setProgress(25);
                    monitor.setNote("Executing SHA-1 query");
                    if (isCancelled()) {
                        return null;
                    }
                    NexusSearchResult single = search.searchBySha1(request.sha1(), 0);
                    setProgress(90);
                    monitor.setNote("Processing results");
                    return single;
                }

                List<NexusArtifact> allArtifacts = new ArrayList<>();
                for (int page = 0; page < MAX_PAGES && !isCancelled(); page++) {
                    int baseProgress = 10;
                    int pageRange = 80;
                    int pageProgress = baseProgress + (pageRange * page) / MAX_PAGES;
                    setProgress(pageProgress);
                    monitor.setNote("Loading page " + (page + 1));

                    NexusSearchResult pageResult;
                    switch (request.mode()) {
                        case 0 -> pageResult = search.searchByKeyword(request.keyword(), page);
                        case 2 -> pageResult = search.searchByGav(request.groupId(), request.artifactId(), request.version(), page);
                        case 3 -> pageResult = search.searchByClassName(request.className(), request.fullyQualified(), page);
                        default -> pageResult = null;
                    }

                    if (pageResult == null || pageResult.artifacts() == null || pageResult.artifacts().isEmpty()) {
                        break;
                    }

                    allArtifacts.addAll(pageResult.artifacts());

                    if (monitor.isCanceled()) {
                        cancel(true);
                        break;
                    }
                }

                setProgress(90);
                monitor.setNote("Processing results");
                if (isCancelled()) {
                    return null;
                }

                return new NexusSearchResult(allArtifacts);
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

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMN_NAMES = {
                "Group",
                "Artifact",
                "Version",
                "Date",
                "Classifier",
                "Extension",
                "Repository"
        };

        private final List<NexusArtifact> artifacts = new ArrayList<>();

        public void setArtifacts(List<NexusArtifact> newArtifacts) {
            artifacts.clear();
            if (newArtifacts != null) {
                artifacts.addAll(newArtifacts);
            }
            fireTableDataChanged();
        }

        public NexusArtifact getArtifactAt(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= artifacts.size()) {
                return null;
            }
            return artifacts.get(rowIndex);
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
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private void updateSnippets(NexusArtifact artifact) {
        if (artifact == null) {
            mavenArea.setText("");
            gradleArea.setText("");
            ivyArea.setText("");
            sbtArea.setText("");
            leinArea.setText("");
            grapeArea.setText("");
            buildrArea.setText("");
            bldArea.setText("");
            return;
        }

        String g = artifact.groupId();
        String a = artifact.artifactId();
        String v = artifact.version();
        String c = artifact.classifier();
        String p = artifact.extension();

        mavenArea.setText(buildMavenSnippet(g, a, v, c, p));
        gradleArea.setText(buildGradleSnippet(g, a, v, c, p));
        ivyArea.setText(buildIvySnippet(g, a, v, c, p));
        sbtArea.setText(buildSbtSnippet(g, a, v, c, p));
        leinArea.setText(buildLeinSnippet(g, a, v, c, p));
        grapeArea.setText(buildGrapeSnippet(g, a, v, c, p));
        buildrArea.setText(buildBuildrSnippet(g, a, v, c, p));
        bldArea.setText(buildBldSnippet(g, a, v, c, p));
    }

    private static String buildMavenSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<dependency>\n");
        sb.append("  <groupId>").append(g).append("</groupId>\n");
        sb.append("  <artifactId>").append(a).append("</artifactId>\n");
        sb.append("  <version>").append(v).append("</version>\n");
        if (c != null && !c.isBlank()) {
            sb.append("  <classifier>").append(c).append("</classifier>\n");
        }
        if (p != null && !p.isBlank() && !"jar".equalsIgnoreCase(p)) {
            sb.append("  <type>").append(p).append("</type>\n");
        }
        sb.append("</dependency>\n");
        return sb.toString();
    }

    private static String buildGradleSnippet(String g, String a, String v, String c, String p) {
        String coords;
        if (c != null && !c.isBlank()) {
            if (p != null && !p.isBlank() && !"jar".equalsIgnoreCase(p)) {
                coords = g + ":" + a + ":" + v + ":" + c + "@" + p;
            } else {
                coords = g + ":" + a + ":" + v + ":" + c;
            }
        } else {
            coords = g + ":" + a + ":" + v;
        }
        return "dependencies {\n    implementation \"" + coords + "\"\n}\n";
    }

    private static String buildIvySnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<dependency org=\"").append(g)
                .append("\" name=\"").append(a)
                .append("\" rev=\"").append(v).append("\"");
        if (p != null && !p.isBlank()) {
            sb.append(" type=\"").append(p).append("\"");
        }
        if (c != null && !c.isBlank()) {
            sb.append(" classifier=\"").append(c).append("\"");
        }
        sb.append(" />\n");
        return sb.toString();
    }

    private static String buildSbtSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("libraryDependencies += \"").append(g).append("\" % \"").append(a).append("\" % \"").append(v).append("\"");
        if (c != null && !c.isBlank()) {
            sb.append(" classifier \"").append(c).append("\"");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String buildLeinSnippet(String g, String a, String v, String c, String p) {
        String ga = g + "/" + a;
        StringBuilder sb = new StringBuilder();
        sb.append("[\"").append(ga).append("\" \"").append(v).append("\"");
        if (c != null && !c.isBlank()) {
            sb.append(" :classifier \"").append(c).append("\"");
        }
        if (p != null && !p.isBlank() && !"jar".equalsIgnoreCase(p)) {
            sb.append(" :extension \"").append(p).append("\"");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static String buildGrapeSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("@Grapes(\n");
        sb.append("    @Grab(group='").append(g).append("', module='").append(a).append("', version='").append(v).append("'");
        if (c != null && !c.isBlank()) {
            sb.append(", classifier='").append(c).append("'");
        }
        if (p != null && !p.isBlank() && !"jar".equalsIgnoreCase(p)) {
            sb.append(", type='").append(p).append("'");
        }
        sb.append(")\n)\n");
        return sb.toString();
    }

    private static String buildBuildrSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        String packaging = (p == null || p.isBlank()) ? "jar" : p;
        sb.append("compile '").append(g).append(":").append(a).append(":").append(packaging).append(":").append(v);
        if (c != null && !c.isBlank()) {
            sb.append(":").append(c);
        }
        sb.append("'\n");
        return sb.toString();
    }

    private static String buildBldSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("dependency(\"").append(g).append("\", \"").append(a).append("\", \"").append(v).append("\"");
        if (c != null && !c.isBlank()) {
            sb.append(", classifier=\"").append(c).append("\"");
        }
        if (p != null && !p.isBlank() && !"jar".equalsIgnoreCase(p)) {
            sb.append(", type=\"").append(p).append("\"");
        }
        sb.append(");\n");
        return sb.toString();
    }
}
