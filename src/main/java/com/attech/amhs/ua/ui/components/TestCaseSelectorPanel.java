package com.attech.amhs.ua.ui.components;

import com.attech.amhs.ua.model.TestCase;
import com.attech.amhs.ua.model.TestSubcase;
import com.attech.amhs.ua.repository.TestCaseRepository;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Left-column panel for selecting CTSW test cases and their messages.
 * 
 * Directory Model Display:
 * ┌─ Test Case Directory ──────────────────────────────┐
 * │ ------CASE CTSW0xx │
 * │ ---------- Message 1 │
 * │ ---------- Message 2 │
 * │ ------CASE CTSW0xy │
 * │ ---------- Message 1 │
 * └────────────────────────────────────────────────────┘
 */
public class TestCaseSelectorPanel extends JPanel {

    private final TestCaseRepository repository;
    private DescriptionPanel descriptionPanel; // Reference to display descriptions & logs

    // Tree - displays directory-style structure
    private JTree subcaseTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    // Listener maps
    private final Map<String, Runnable> defaultsLoadedListeners = new HashMap<>();
    private final Map<String, Runnable> selectionChangedListeners = new HashMap<>();
    private final Map<String, Runnable> copyDefaultsListeners = new HashMap<>();
    private final Map<String, Runnable> sendDefaultsListeners = new HashMap<>();
    private final Map<String, Runnable> sendAllSubcasesListeners = new HashMap<>();

    public TestCaseSelectorPanel(TestCaseRepository repository) {
        this.repository = repository;
        initUI();
    }

    // ── UI construction ───────────────────────────────────────────────────

    private void initUI() {
        setBorder(new TitledBorder("Test Case Directory"));
        setLayout(new BorderLayout(4, 4));

        add(buildCenterTree(), BorderLayout.CENTER);

        populateTestCases();
    }

    /** Tree with directory-style display */
    private JScrollPane buildCenterTree() {
        // ── Tree ─────────────────────────────────────────────────────────
        rootNode = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(rootNode);
        subcaseTree = new JTree(treeModel);
        subcaseTree.setRootVisible(false);
        subcaseTree.setShowsRootHandles(true);
        subcaseTree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        subcaseTree.setCellRenderer(new SubcaseCellRenderer());
        subcaseTree.addTreeSelectionListener(e -> handleTreeSelectionChanged());

        JScrollPane treeScroll = new JScrollPane(subcaseTree);
        treeScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        return treeScroll;
    }

    // ── Event handlers ────────────────────────────────────────────────────

    private void handleTreeSelectionChanged() {
        SubcaseNode sn = getSelectedNode();
        if (sn == null) {
            return;
        }
        // If case header node is selected, display test case description
        if (sn.testcase != null) {
            displayTestCaseDescription(sn.testcase);
            return;
        }
        if (sn.isDefault) {
            // Placeholder node - clear description
            return;
        } else if (sn.subcase != null) {
            // Display subcase description with expectations in the DescriptionPanel
            displaySubcaseDescription(sn.subcase);

            if (!sn.subcase.getAmhsDefaults().isEmpty()) {
                selectionChangedListeners.values().forEach(Runnable::run);
            }
        }
    }

    // ── External Actions (called by main UI) ──────────────────────────────

    public void triggerLoadDefaults() {
        SubcaseNode sn = getSelectedNode();
        TestSubcase sc = getSelectedSubcase();
        if (sn != null && sn.isDefault) {
            JOptionPane.showMessageDialog(this,
                    "No valid subcase selected.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
        } else if (sc != null && !sc.getAmhsDefaults().isEmpty()) {
            defaultsLoadedListeners.values().forEach(Runnable::run);
        } else if (sn == null) {
            JOptionPane.showMessageDialog(this,
                    "No message selected.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "No AMHS defaults configured for this subcase.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void triggerSendDefaults() {
        SubcaseNode sn = getSelectedNode();
        TestSubcase sc = getSelectedSubcase();
        if (sn != null && sn.isDefault) {
            JOptionPane.showMessageDialog(this,
                    "No valid subcase selected.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
        } else if (sc != null && !sc.getAmhsDefaults().isEmpty()) {
            sendDefaultsListeners.values().forEach(Runnable::run);
        } else if (sn == null) {
            JOptionPane.showMessageDialog(this,
                    "No message selected.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "No AMHS defaults configured for this subcase.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void triggerSendAllSubcases() {
        TestCase tc = getSelectedTestCase();
        if (tc == null) {
            JOptionPane.showMessageDialog(this,
                    "No test case selected.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (tc.getSubcases() == null || tc.getSubcases().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No subcases defined for this test case.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Trigger the send all subcases listener
        sendAllSubcasesListeners.values().forEach(Runnable::run);
    }

    // ── Data helpers ──────────────────────────────────────────────────────

    private void populateTestCases() {
        rootNode.removeAllChildren();
        if (repository != null && repository.getTestCasesList() != null) {
            for (TestCase tc : repository.getTestCasesList()) {
                DefaultMutableTreeNode caseNode = new DefaultMutableTreeNode(
                        new SubcaseNode("------CASE " + tc.getId(), tc, true));

                if (tc.getSubcases() != null && !tc.getSubcases().isEmpty()) {
                    int idx = 1;
                    for (TestSubcase sc : tc.getSubcases()) {
                        String label = "---------- Message " + idx;
                        caseNode.add(new DefaultMutableTreeNode(
                                new SubcaseNode(label, sc, false)));
                        idx++;
                    }
                } else {
                    caseNode.add(new DefaultMutableTreeNode(
                            new SubcaseNode("---------- No messages defined", (TestSubcase) null, true)));
                }

                rootNode.add(caseNode);
            }
        }
        treeModel.reload();
    }

    private SubcaseNode getSelectedNode() {
        TreePath path = subcaseTree.getSelectionPath();
        if (path == null)
            return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object uo = node.getUserObject();
        return (uo instanceof SubcaseNode) ? (SubcaseNode) uo : null;
    }

    // ── Public API (used by AMHSMessageUI) ────────────────────────────────

    public TestCase getSelectedTestCase() {
        TreePath path = subcaseTree.getSelectionPath();
        if (path == null)
            return null;
        Object[] nodes = path.getPath();
        for (int i = nodes.length - 1; i >= 0; i--) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes[i];
            Object uo = node.getUserObject();
            if (uo instanceof SubcaseNode) {
                SubcaseNode sn = (SubcaseNode) uo;
                if (sn.testcase != null) {
                    return sn.testcase;
                }
            }
        }
        return null;
    }

    /** Returns null when no valid subcase is selected */
    public TestSubcase getSelectedSubcase() {
        SubcaseNode sn = getSelectedNode();
        return (sn != null && !sn.isDefault) ? sn.subcase : null;
    }

    public Map<String, String> getSelectedSubcaseDefaults() {
        TestSubcase sc = getSelectedSubcase();
        return sc != null ? new HashMap<>(sc.getAmhsDefaults()) : new HashMap<>();
    }

    /** Refresh the tree after external repository changes */
    public void refresh() {
        populateTestCases();
        subcaseTree.repaint();
    }

    /** Set the description panel reference for displaying descriptions & logs */
    public void setDescriptionPanel(DescriptionPanel panel) {
        this.descriptionPanel = panel;
    }

    // Listener registration
    public void addDefaultsLoadedListener(String key, Runnable r) {
        defaultsLoadedListeners.put(key, r);
    }

    /** Invoked when a configured subcase is selected in the tree. */
    public void addSelectionChangedListener(String key, Runnable r) {
        selectionChangedListeners.put(key, r);
    }

    public void addCopyDefaultsListener(String key, Runnable r) {
        copyDefaultsListeners.put(key, r);
    }

    public void addSendDefaultsListener(String key, Runnable r) {
        sendDefaultsListeners.put(key, r);
    }

    public void addSendAllSubcasesListener(String key, Runnable r) {
        sendAllSubcasesListeners.put(key, r);
    }

    // ── Inner classes ─────────────────────────────────────────────────────

    /** Data object held in each JTree node */
    static class SubcaseNode {
        final String label;
        final TestSubcase subcase; // null for placeholder nodes and case nodes
        final TestCase testcase; // non-null only for case header nodes
        final boolean isDefault;

        SubcaseNode(String label, TestSubcase subcase, boolean isDefault) {
            this.label = label;
            this.subcase = subcase;
            this.testcase = null;
            this.isDefault = isDefault;
        }

        SubcaseNode(String label, TestCase testcase, boolean isDefault) {
            this.label = label;
            this.subcase = null;
            this.testcase = testcase;
            this.isDefault = isDefault;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Colour-codes tree nodes by result */
    private static class SubcaseCellRenderer extends DefaultTreeCellRenderer {

        private static final Color PASS_FG = new Color(0, 120, 0);
        private static final Color FAIL_FG = new Color(170, 0, 0);
        private static final Color CASE_FG = new Color(0, 0, 180);

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(
                    tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(null); // remove default folder/leaf icons

            if (value instanceof DefaultMutableTreeNode) {
                Object uo = ((DefaultMutableTreeNode) value).getUserObject();
                if (uo instanceof SubcaseNode) {
                    SubcaseNode sn = (SubcaseNode) uo;
                    if (sn.isDefault && sn.testcase != null) {
                        // Case header node - bold blue
                        setFont(getFont().deriveFont(Font.BOLD));
                        if (!sel)
                            setForeground(CASE_FG);
                    } else if (sn.isDefault) {
                        // Placeholder node - italic gray
                        setFont(getFont().deriveFont(Font.ITALIC));
                        if (!sel)
                            setForeground(Color.GRAY);
                    } else if (sn.subcase != null) {
                        String result = sn.subcase.getResult();
                        if (!sel) {
                            if ("PASS".equals(result))
                                setForeground(PASS_FG);
                            else if ("FAIL".equals(result))
                                setForeground(FAIL_FG);
                        }
                    }
                }
            }
            return this;
        }
    }

    // ── Helper methods for displaying descriptions ────────────────────────

    /**
     * Display test case description in the parent UI's action logs panel
     */
    private void displayTestCaseDescription(TestCase tc) {
        if (descriptionPanel != null && tc != null) {
            descriptionPanel.displayTestCaseDescription(tc.getId(), tc.getName(), tc.getDescription(),
                    "See individual subcases for detailed criteria", "EUR Doc 047 Appendix A");
        }
    }

    /**
     * Display subcase description in the parent UI's description panel
     */
    private void displaySubcaseDescription(TestSubcase sc) {
        if (descriptionPanel != null && sc != null) {
            descriptionPanel.displaySubcaseDescription(sc.getId(), sc.getName(), sc.getDescription(),
                    sc.getExpectation());
        }
    }
}
