/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for configuring and applying gremlin character substitutions.
 * Shows a table with checkboxes, search characters, and replacement strings.
 * Users can add, remove, and edit substitutions.
 */
public class ZapGremlinsDialog extends JDialog {

    private final GremlinTableModel tableModel;
    private boolean zapped = false;
    private boolean saved = false;

    public ZapGremlinsDialog(Window owner, Preferences preferences) {
        super(owner, "Zap Gremlins", ModalityType.APPLICATION_MODAL);

        List<String[]> gremlins = preferences.getGremlins();
        tableModel = new GremlinTableModel(gremlins);

        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.setRowHeight(24);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Show Unicode code point in tooltip for the search column
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                if (value instanceof String s && !s.isEmpty()) {
                    StringBuilder tip = new StringBuilder();
                    for (int i = 0; i < s.length(); i++) {
                        if (i > 0) tip.append(", ");
                        tip.append(String.format("U+%04X", (int) s.charAt(i)));
                    }
                    setToolTipText(tip.toString());
                } else {
                    setToolTipText(null);
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        // Add/Remove row buttons
        JButton addBtn = new JButton("+");
        addBtn.setToolTipText(Messages.get("dialog.zapGremlins.add"));
        addBtn.addActionListener(e -> {
            tableModel.addRow(new String[]{"true", "", ""});
            int newRow = tableModel.getRowCount() - 1;
            table.editCellAt(newRow, 1);
            table.scrollRectToVisible(table.getCellRect(newRow, 1, true));
        });

        JButton removeBtn = new JButton("\u2212");
        removeBtn.setToolTipText(Messages.get("dialog.zapGremlins.remove"));
        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
            }
        });

        JPanel addRemovePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addRemovePanel.add(addBtn);
        addRemovePanel.add(removeBtn);

        // Bottom buttons
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            preferences.setGremlins(tableModel.getData());
            preferences.save();
            saved = true;
            JOptionPane.showMessageDialog(this, Messages.get("dialog.zapGremlins.saved"),
                "Save", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton cancelBtn = new JButton(Messages.get("dialog.zapGremlins.cancel"));
        cancelBtn.addActionListener(e -> dispose());

        JButton zapBtn = new JButton(Messages.get("dialog.zapGremlins.zap"));
        zapBtn.addActionListener(e -> {
            preferences.setGremlins(tableModel.getData());
            preferences.save();
            saved = true;
            zapped = true;
            dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);
        buttonPanel.add(zapBtn);

        // Layout
        JPanel contentPanel = new JPanel(new BorderLayout(8, 8));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(new JLabel(Messages.get("dialog.zapGremlins.configure")), BorderLayout.NORTH);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(addRemovePanel, BorderLayout.WEST);
        southPanel.add(buttonPanel, BorderLayout.EAST);
        contentPanel.add(southPanel, BorderLayout.SOUTH);

        add(contentPanel);

        // Escape key closes
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { dispose(); }
        });
        getRootPane().setDefaultButton(zapBtn);

        pack();
        setLocationRelativeTo(owner);
        setResizable(true);
    }

    /** Returns true if the user clicked Zap. */
    public boolean isZapped() { return zapped; }

    /** Returns true if the user clicked Save or Zap. */
    public boolean isSaved() { return saved; }

    /** Get the current substitution data from the table. */
    public List<String[]> getData() { return tableModel.getData(); }

    // --- Table Model ---

    private static class GremlinTableModel extends AbstractTableModel {
        private final List<String[]> data;
        private static final String[] COLUMNS = {Messages.get("dialog.zapGremlins.on"), Messages.get("dialog.zapGremlins.character"), Messages.get("dialog.zapGremlins.replacement")};

        GremlinTableModel(List<String[]> source) {
            // Deep copy
            data = new ArrayList<>();
            for (String[] row : source) {
                data.add(new String[]{row[0], row[1], row[2]});
            }
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) { return true; }

        @Override
        public Object getValueAt(int row, int col) {
            String[] r = data.get(row);
            if (col == 0) return "true".equals(r[0]);
            return r[col];
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            String[] r = data.get(row);
            if (col == 0) {
                r[0] = Boolean.TRUE.equals(value) ? "true" : "false";
            } else {
                r[col] = value != null ? value.toString() : "";
            }
            fireTableCellUpdated(row, col);
        }

        void addRow(String[] row) {
            data.add(row);
            fireTableRowsInserted(data.size() - 1, data.size() - 1);
        }

        void removeRow(int row) {
            data.remove(row);
            fireTableRowsDeleted(row, row);
        }

        List<String[]> getData() {
            return new ArrayList<>(data);
        }
    }
}
