/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * TableDialog.java
 *
 * A modal dialog for inserting or editing a Markdown table. Provides a mini
 * spreadsheet with editable column headers, a row header column, clipboard
 * support (cut/copy/paste), and checkboxes to control header output.
 */
package com.glowingcat;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class TableDialog extends JDialog {

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JCheckBox columnHeadersBox;
    private final JCheckBox rowHeadersBox;
    private final List<String> rowHeaderValues = new ArrayList<>();
    private final List<Alignment> columnAlignments = new ArrayList<>();
    private JList<String> rowHeaderList;
    private boolean confirmed = false;

    private static final int DEFAULT_ROWS = 4;
    private static final int DEFAULT_COLS = 3;

    /** Column alignment options for markdown table output. */
    public enum Alignment {
        NONE, LEFT, CENTER, RIGHT;

        /** Returns a small unicode indicator character. */
        public String icon() {
            return switch (this) {
                case LEFT -> "\u25C0";    // ◀
                case CENTER -> "\u25C6";  // ◆
                case RIGHT -> "\u25B6";   // ▶
                default -> "\u25CB";      // ○
            };
        }
    }

    public TableDialog(JFrame owner, String tableText) {
        super(owner, "Insert Table", true);

        // Parse existing table or create empty model
        String[][] data;
        String[] colHeaders;
        boolean detectedColHeaders = true;
        boolean detectedRowHeaders = false;

        if (tableText != null && tableText.contains("|")) {
            List<String[]> parsed = parseMarkdownTable(tableText);
            detectedColHeaders = hasSeparatorRow(tableText);

            if (parsed.size() > 0) {
                if (detectedColHeaders && parsed.size() > 1) {
                    // First row is column headers
                    colHeaders = parsed.get(0);

                    // Detect row headers: check if first column of data rows are all bold
                    detectedRowHeaders = detectRowHeaders(parsed);

                    int startCol = detectedRowHeaders ? 1 : 0;
                    int dataCols = colHeaders.length - startCol;
                    int dataRows = parsed.size() - 1;

                    // Extract row headers if detected
                    if (detectedRowHeaders) {
                        for (int i = 1; i < parsed.size(); i++) {
                            String cell = parsed.get(i)[0];
                            if (cell.startsWith("**") && cell.endsWith("**")) {
                                cell = cell.substring(2, cell.length() - 2);
                            }
                            rowHeaderValues.add(cell);
                        }
                        String[] trimmedHeaders = new String[dataCols];
                        System.arraycopy(colHeaders, 1, trimmedHeaders, 0, dataCols);
                        colHeaders = trimmedHeaders;
                    }

                    // Replace empty headers with defaults
                    for (int i = 0; i < colHeaders.length; i++) {
                        if (colHeaders[i] == null || colHeaders[i].trim().isEmpty()) {
                            colHeaders[i] = "Col " + (i + 1);
                        }
                    }

                    data = new String[Math.max(dataRows, DEFAULT_ROWS)][dataCols];
                    for (int i = 1; i < parsed.size(); i++) {
                        String[] row = parsed.get(i);
                        for (int c = 0; c < dataCols; c++) {
                            int srcCol = c + startCol;
                            String cell = srcCol < row.length ? row[srcCol] : "";
                            if (cell.startsWith("**") && cell.endsWith("**")) {
                                cell = cell.substring(2, cell.length() - 2);
                            }
                            data[i - 1][c] = cell;
                        }
                    }
                    for (int i = dataRows; i < data.length; i++) {
                        data[i] = new String[dataCols];
                        for (int j = 0; j < dataCols; j++) data[i][j] = "";
                    }
                } else {
                    // No column headers — all rows are data
                    // Check for row headers in the first column
                    detectedRowHeaders = detectRowHeadersAllRows(parsed);

                    int startCol = detectedRowHeaders ? 1 : 0;
                    int maxCols = parsed.get(0).length;
                    int dataCols = maxCols - startCol;
                    int dataRows = parsed.size();

                    colHeaders = createDefaultHeaders(dataCols);

                    if (detectedRowHeaders) {
                        for (int i = 0; i < parsed.size(); i++) {
                            String cell = parsed.get(i)[0];
                            if (cell.startsWith("**") && cell.endsWith("**")) {
                                cell = cell.substring(2, cell.length() - 2);
                            }
                            rowHeaderValues.add(cell);
                        }
                    }

                    data = new String[Math.max(dataRows, DEFAULT_ROWS)][dataCols];
                    for (int i = 0; i < parsed.size(); i++) {
                        String[] row = parsed.get(i);
                        for (int c = 0; c < dataCols; c++) {
                            int srcCol = c + startCol;
                            String cell = srcCol < row.length ? row[srcCol] : "";
                            if (cell.startsWith("**") && cell.endsWith("**")) {
                                cell = cell.substring(2, cell.length() - 2);
                            }
                            data[i][c] = cell;
                        }
                    }
                    for (int i = dataRows; i < data.length; i++) {
                        data[i] = new String[dataCols];
                        for (int j = 0; j < dataCols; j++) data[i][j] = "";
                    }
                }
            } else {
                colHeaders = createDefaultHeaders(DEFAULT_COLS);
                data = createEmptyData(DEFAULT_ROWS, DEFAULT_COLS);
            }
        } else {
            colHeaders = createDefaultHeaders(DEFAULT_COLS);
            data = createEmptyData(DEFAULT_ROWS, DEFAULT_COLS);
            detectedColHeaders = true;
            detectedRowHeaders = false;
        }

        // Pad row headers to match data rows
        while (rowHeaderValues.size() < data.length) {
            rowHeaderValues.add("Row " + (rowHeaderValues.size() + 1));
        }

        // Parse column alignments from separator row
        if (tableText != null && detectedColHeaders) {
            parseAlignments(tableText, detectedRowHeaders);
        }
        // Pad alignments to match column count
        while (columnAlignments.size() < colHeaders.length) {
            columnAlignments.add(Alignment.NONE);
        }

        tableModel = new DefaultTableModel(data, colHeaders);
        table = new JTable(tableModel);
        table.setCellSelectionEnabled(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowGrid(true);
        table.setGridColor(Color.LIGHT_GRAY);

        // Style column headers: bold text with light gray background and alignment icon
        table.getTableHeader().setDefaultRenderer(new javax.swing.table.TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBackground(new Color(240, 240, 240));
                panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.LIGHT_GRAY));

                JLabel textLabel = new JLabel(value != null ? value.toString() : "");
                textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
                textLabel.setHorizontalAlignment(SwingConstants.CENTER);
                panel.add(textLabel, BorderLayout.CENTER);

                Alignment align = column < columnAlignments.size() ? columnAlignments.get(column) : Alignment.NONE;
                JLabel iconLabel = new JLabel(align.icon());
                iconLabel.setForeground(Color.GRAY);
                iconLabel.setFont(iconLabel.getFont().deriveFont(10f));
                iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 4));
                panel.add(iconLabel, BorderLayout.EAST);

                return panel;
            }
        });

        // Row header panel (fixed first column)
        rowHeaderList = new JList<>(new AbstractListModel<>() {
            @Override
            public int getSize() { return tableModel.getRowCount(); }
            @Override
            public String getElementAt(int index) {
                return index < rowHeaderValues.size() ? rowHeaderValues.get(index) : "";
            }
        });
        rowHeaderList.setFixedCellWidth(80);
        rowHeaderList.setFixedCellHeight(table.getRowHeight());
        rowHeaderList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, false, false);
                label.setBackground(new Color(240, 240, 240));
                label.setOpaque(true);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.LIGHT_GRAY));
                return label;
            }
        });

        // Double-click to edit row headers
        rowHeaderList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = rowHeaderList.locationToIndex(e.getPoint());
                    if (idx >= 0 && idx < rowHeaderValues.size()) {
                        String current = rowHeaderValues.get(idx);
                        String newVal = JOptionPane.showInputDialog(
                                TableDialog.this, "Row header:", current);
                        if (newVal != null) {
                            rowHeaderValues.set(idx, newVal);
                            rowHeaderList.repaint();
                        }
                    }
                }
            }
        });

        // Click on header: right side for alignment popup, double-click to edit name
        JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col < 0) return;

                Rectangle headerRect = header.getHeaderRect(col);
                int clickX = e.getX() - headerRect.x;
                if (clickX > headerRect.width - 24) {
                    showAlignmentPopup(e, col);
                } else if (e.getClickCount() == 2) {
                    editColumnHeader(col);
                }
            }
        });

        // Clipboard support (Ctrl/Cmd + X, C, V)
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask);
        KeyStroke cut = KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutMask);
        KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask);

        table.getInputMap().put(copy, "copy");
        table.getInputMap().put(cut, "cut");
        table.getInputMap().put(paste, "paste");

        table.getActionMap().put("copy", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { copySelection(false); }
        });
        table.getActionMap().put("cut", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { copySelection(true); }
        });
        table.getActionMap().put("paste", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { pasteClipboard(); }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setRowHeaderView(rowHeaderList);
        scrollPane.setPreferredSize(new Dimension(600, 220));

        // Control buttons (vertical, right side)
        JButton addRowBtn = new JButton("+ Row");
        JButton removeRowBtn = new JButton("- Row");
        JButton addColBtn = new JButton("+ Column");
        JButton removeColBtn = new JButton("- Column");

        // Initially disable delete buttons (no selection)
        removeRowBtn.setEnabled(false);
        removeColBtn.setEnabled(false);

        // Enable/disable delete buttons based on selection
        table.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = table.getSelectedRowCount() > 0 && table.getSelectedColumnCount() > 0;
            removeRowBtn.setEnabled(hasSelection);
            removeColBtn.setEnabled(hasSelection);
        });
        table.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = table.getSelectedRowCount() > 0 && table.getSelectedColumnCount() > 0;
            removeRowBtn.setEnabled(hasSelection);
            removeColBtn.setEnabled(hasSelection);
        });

        addRowBtn.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            int insertAt = (selectedRow >= 0) ? selectedRow + 1 : tableModel.getRowCount();
            tableModel.insertRow(insertAt, new String[tableModel.getColumnCount()]);
            rowHeaderValues.add(insertAt, "Row " + (insertAt + 1));
            rowHeaderList.updateUI();
        });
        removeRowBtn.addActionListener(e -> {
            int[] selectedRows = table.getSelectedRows();
            if (selectedRows.length > 0 && tableModel.getRowCount() > selectedRows.length) {
                for (int i = selectedRows.length - 1; i >= 0; i--) {
                    int row = selectedRows[i];
                    tableModel.removeRow(row);
                    if (row < rowHeaderValues.size()) rowHeaderValues.remove(row);
                }
                rowHeaderList.updateUI();
            }
        });
        addColBtn.addActionListener(e -> {
            int selectedCol = table.getSelectedColumn();
            if (selectedCol >= 0 && selectedCol < tableModel.getColumnCount() - 1) {
                // Insert after selected column by rebuilding model
                int colCount = tableModel.getColumnCount();
                int insertAt = selectedCol + 1;
                int rows = tableModel.getRowCount();
                String[] newHeaders = new String[colCount + 1];
                for (int c = 0; c < colCount + 1; c++) {
                    if (c < insertAt) newHeaders[c] = tableModel.getColumnName(c);
                    else if (c == insertAt) newHeaders[c] = "Col " + (c + 1);
                    else newHeaders[c] = tableModel.getColumnName(c - 1);
                }
                String[][] newData = new String[rows][colCount + 1];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < colCount + 1; c++) {
                        if (c < insertAt) {
                            Object val = tableModel.getValueAt(r, c);
                            newData[r][c] = val != null ? val.toString() : "";
                        } else if (c == insertAt) {
                            newData[r][c] = "";
                        } else {
                            Object val = tableModel.getValueAt(r, c - 1);
                            newData[r][c] = val != null ? val.toString() : "";
                        }
                    }
                }
                tableModel.setDataVector(newData, newHeaders);
            } else {
                tableModel.addColumn("Col " + (tableModel.getColumnCount() + 1));
            }
        });
        removeColBtn.addActionListener(e -> {
            int[] selectedCols = table.getSelectedColumns();
            if (selectedCols.length == 0) return;
            int colCount = tableModel.getColumnCount();
            int remaining = colCount - selectedCols.length;
            if (remaining < 1) return;

            java.util.Set<Integer> removeCols = new java.util.HashSet<>();
            for (int c : selectedCols) removeCols.add(c);

            int rows = tableModel.getRowCount();
            String[] newHeaders = new String[remaining];
            int idx = 0;
            for (int c = 0; c < colCount; c++) {
                if (!removeCols.contains(c)) {
                    newHeaders[idx++] = tableModel.getColumnName(c);
                }
            }
            String[][] newData = new String[rows][remaining];
            for (int r = 0; r < rows; r++) {
                idx = 0;
                for (int c = 0; c < colCount; c++) {
                    if (!removeCols.contains(c)) {
                        Object val = tableModel.getValueAt(r, c);
                        newData[r][idx++] = val != null ? val.toString() : "";
                    }
                }
            }
            tableModel.setDataVector(newData, newHeaders);
        });

        // Vertical button panel on the right
        Dimension btnSize = new Dimension(110, 28);
        addRowBtn.setMaximumSize(btnSize);
        addRowBtn.setPreferredSize(btnSize);
        removeRowBtn.setMaximumSize(btnSize);
        removeRowBtn.setPreferredSize(btnSize);
        addColBtn.setMaximumSize(btnSize);
        addColBtn.setPreferredSize(btnSize);
        removeColBtn.setMaximumSize(btnSize);
        removeColBtn.setPreferredSize(btnSize);

        JPanel sideButtonPanel = new JPanel();
        sideButtonPanel.setLayout(new BoxLayout(sideButtonPanel, BoxLayout.Y_AXIS));
        sideButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        sideButtonPanel.add(addRowBtn);
        sideButtonPanel.add(Box.createVerticalStrut(6));
        sideButtonPanel.add(removeRowBtn);
        sideButtonPanel.add(Box.createVerticalStrut(12));
        sideButtonPanel.add(addColBtn);
        sideButtonPanel.add(Box.createVerticalStrut(6));
        sideButtonPanel.add(removeColBtn);

        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        columnHeadersBox = new JCheckBox("Column Headers", detectedColHeaders);
        rowHeadersBox = new JCheckBox("Row Headers", detectedRowHeaders);
        optionsPanel.add(columnHeadersBox);
        optionsPanel.add(rowHeadersBox);

        // Hint
        JLabel hintLabel = new JLabel("Double-click column or row headers to edit them. Cmd/Ctrl+C/X/V for clipboard.");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        hintLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));

        // Dialog buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            confirmed = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Layout
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        mainPanel.add(optionsPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(sideButtonPanel, BorderLayout.EAST);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(hintLabel, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
        getRootPane().setDefaultButton(saveButton);
        pack();
        setLocationRelativeTo(owner);
        setResizable(true);
    }

    private void copySelection(boolean isCut) {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;

        StringBuilder sb = new StringBuilder();
        for (int r : rows) {
            for (int i = 0; i < cols.length; i++) {
                Object val = tableModel.getValueAt(r, cols[i]);
                sb.append(val != null ? val.toString() : "");
                if (i < cols.length - 1) sb.append("\t");
            }
            sb.append("\n");
        }

        StringSelection sel = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);

        if (isCut) {
            for (int r : rows)
                for (int c : cols)
                    tableModel.setValueAt("", r, c);
        }
    }

    private void pasteClipboard() {
        try {
            String data = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data == null) return;

            int startRow = table.getSelectedRow();
            int startCol = table.getSelectedColumn();
            if (startRow < 0) startRow = 0;
            if (startCol < 0) startCol = 0;

            String[] lines = data.split("\n", -1);
            // Remove trailing empty line if present (common with clipboard)
            if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
                lines = java.util.Arrays.copyOf(lines, lines.length - 1);
            }

            // Determine how many columns are needed
            int maxPasteCols = 0;
            for (String line : lines) {
                String[] cells = line.split("\t", -1);
                maxPasteCols = Math.max(maxPasteCols, cells.length);
            }

            // Add columns if needed
            int neededCols = startCol + maxPasteCols;
            int currentCols = tableModel.getColumnCount();
            for (int c = currentCols; c < neededCols; c++) {
                tableModel.addColumn("Col " + (c + 1));
            }

            // Add rows and paste data
            for (int r = 0; r < lines.length; r++) {
                String[] cells = lines[r].split("\t", -1);
                int targetRow = startRow + r;
                while (targetRow >= tableModel.getRowCount()) {
                    tableModel.addRow(new String[tableModel.getColumnCount()]);
                    rowHeaderValues.add("Row " + (rowHeaderValues.size() + 1));
                }
                for (int c = 0; c < cells.length; c++) {
                    int targetCol = startCol + c;
                    if (targetCol < tableModel.getColumnCount()) {
                        tableModel.setValueAt(cells[c], targetRow, targetCol);
                    }
                }
            }
            rowHeaderList.updateUI();
        } catch (Exception ex) {
            // Ignore clipboard errors
        }
    }

    private void editColumnHeader(int col) {
        String current = tableModel.getColumnName(col);
        String newName = JOptionPane.showInputDialog(this, "Column header:", current);
        if (newName != null) {
            int colCount = tableModel.getColumnCount();
            String[] headers = new String[colCount];
            for (int i = 0; i < colCount; i++)
                headers[i] = (i == col) ? newName : tableModel.getColumnName(i);
            int rows = tableModel.getRowCount();
            String[][] data = new String[rows][colCount];
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < colCount; c++) {
                    Object val = tableModel.getValueAt(r, c);
                    data[r][c] = val != null ? val.toString() : "";
                }
            tableModel.setDataVector(data, headers);
        }
    }

    /**
     * Shows a popup menu to select column alignment.
     */
    private void showAlignmentPopup(MouseEvent e, int col) {
        JPopupMenu popup = new JPopupMenu();
        for (Alignment a : Alignment.values()) {
            JMenuItem item = new JMenuItem(a.icon() + "  " + a.name().charAt(0) + a.name().substring(1).toLowerCase());
            item.addActionListener(ev -> {
                while (columnAlignments.size() <= col) columnAlignments.add(Alignment.NONE);
                columnAlignments.set(col, a);
                table.getTableHeader().repaint();
            });
            popup.add(item);
        }
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Parses column alignments from the separator row of a markdown table.
     * Format: :--- (left), :---: (center), ---: (right), --- (none).
     */
    private void parseAlignments(String tableText, boolean hasRowHeaders) {
        String[] lines = tableText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!isSeparatorRow(line)) continue;

            // Found separator row - parse alignments
            if (line.startsWith("|")) line = line.substring(1);
            if (line.endsWith("|")) line = line.substring(0, line.length() - 1);
            String[] cells = line.split("\\|", -1);

            int startCol = hasRowHeaders ? 1 : 0;
            for (int i = startCol; i < cells.length; i++) {
                String cell = cells[i].trim();
                boolean leftColon = cell.startsWith(":");
                boolean rightColon = cell.endsWith(":");
                if (leftColon && rightColon) {
                    columnAlignments.add(Alignment.CENTER);
                } else if (rightColon) {
                    columnAlignments.add(Alignment.RIGHT);
                } else if (leftColon) {
                    columnAlignments.add(Alignment.LEFT);
                } else {
                    columnAlignments.add(Alignment.NONE);
                }
            }
            break; // only process first separator row
        }
    }

    /**
     * Detects if the first column of data rows are bold (row headers).
     */
    private boolean detectRowHeaders(List<String[]> parsed) {
        if (parsed.size() < 2) return false;
        int boldCount = 0;
        int nonEmptyCount = 0;
        for (int i = 1; i < parsed.size(); i++) {
            String cell = parsed.get(i)[0].trim();
            if (!cell.isEmpty()) {
                nonEmptyCount++;
                if (cell.startsWith("**") && cell.endsWith("**")) {
                    boldCount++;
                }
            }
        }
        return nonEmptyCount > 0 && boldCount >= nonEmptyCount / 2;
    }

    /**
     * Detects row headers when there are no column headers (checks all rows including first).
     */
    private boolean detectRowHeadersAllRows(List<String[]> parsed) {
        if (parsed.isEmpty()) return false;
        int boldCount = 0;
        int nonEmptyCount = 0;
        for (String[] row : parsed) {
            String cell = row[0].trim();
            if (!cell.isEmpty()) {
                nonEmptyCount++;
                if (cell.startsWith("**") && cell.endsWith("**")) {
                    boldCount++;
                }
            }
        }
        return nonEmptyCount > 0 && boldCount >= nonEmptyCount / 2;
    }

    /**
     * Checks if the markdown text contains a separator row (indicating column headers).
     */
    private boolean hasSeparatorRow(String text) {
        for (String line : text.split("\n")) {
            if (isSeparatorRow(line.trim())) return true;
        }
        return false;
    }

    private List<String[]> parseMarkdownTable(String text) {
        List<String[]> rows = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (isSeparatorRow(line)) continue;
            if (line.startsWith("|")) line = line.substring(1);
            if (line.endsWith("|")) line = line.substring(0, line.length() - 1);
            String[] cells = line.split("\\|", -1);
            for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
            rows.add(cells);
        }
        int maxCols = 0;
        for (String[] row : rows) maxCols = Math.max(maxCols, row.length);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).length < maxCols) {
                String[] padded = new String[maxCols];
                System.arraycopy(rows.get(i), 0, padded, 0, rows.get(i).length);
                for (int j = rows.get(i).length; j < maxCols; j++) padded[j] = "";
                rows.set(i, padded);
            }
        }
        return rows;
    }

    private boolean isSeparatorRow(String line) {
        String stripped = line.replace("|", "").replace("-", "")
                .replace(":", "").replace(" ", "");
        return stripped.isEmpty() && line.contains("-");
    }

    private String[] createDefaultHeaders(int cols) {
        String[] headers = new String[cols];
        for (int i = 0; i < cols; i++) headers[i] = "Col " + (i + 1);
        return headers;
    }

    private String[][] createEmptyData(int rows, int cols) {
        String[][] data = new String[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) data[r][c] = "";
        return data;
    }

    public boolean isConfirmed() { return confirmed; }

    public String getMarkdownTable() {
        int cols = tableModel.getColumnCount();
        int rows = tableModel.getRowCount();
        boolean useColHeaders = columnHeadersBox.isSelected();
        boolean useRowHeaders = rowHeadersBox.isSelected();

        // Find last non-empty row
        int lastRow = -1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Object val = tableModel.getValueAt(r, c);
                if (val != null && !val.toString().trim().isEmpty()) { lastRow = r; break; }
            }
            if (lastRow < r && useRowHeaders && r < rowHeaderValues.size()
                    && !rowHeaderValues.get(r).isEmpty()) {
                lastRow = r;
            }
        }
        if (lastRow < 0 && !useColHeaders) return "";

        // Total columns in output
        int outputCols = useRowHeaders ? cols + 1 : cols;

        // Calculate column widths
        int[] widths = new int[outputCols];
        for (int c = 0; c < outputCols; c++) widths[c] = 3;

        if (useColHeaders) {
            if (useRowHeaders) {
                widths[0] = Math.max(widths[0], 3); // row header column header is empty
                for (int c = 0; c < cols; c++)
                    widths[c + 1] = Math.max(widths[c + 1], tableModel.getColumnName(c).length());
            } else {
                for (int c = 0; c < cols; c++)
                    widths[c] = Math.max(widths[c], tableModel.getColumnName(c).length());
            }
        }

        for (int r = 0; r <= lastRow; r++) {
            if (useRowHeaders) {
                String rh = r < rowHeaderValues.size() ? rowHeaderValues.get(r) : "";
                String boldRh = !rh.isEmpty() ? "**" + rh + "**" : "";
                widths[0] = Math.max(widths[0], boldRh.length());
                for (int c = 0; c < cols; c++) {
                    Object val = tableModel.getValueAt(r, c);
                    String cell = val != null ? val.toString().trim() : "";
                    widths[c + 1] = Math.max(widths[c + 1], cell.length());
                }
            } else {
                for (int c = 0; c < cols; c++) {
                    Object val = tableModel.getValueAt(r, c);
                    String cell = val != null ? val.toString().trim() : "";
                    widths[c] = Math.max(widths[c], cell.length());
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        // Column headers (always output header + separator for valid markdown table)
        sb.append("|");
        if (useColHeaders) {
            if (useRowHeaders) {
                sb.append(" ").append(pad("", widths[0])).append(" |");
                for (int c = 0; c < cols; c++)
                    sb.append(" ").append(pad(tableModel.getColumnName(c), widths[c + 1])).append(" |");
            } else {
                for (int c = 0; c < cols; c++)
                    sb.append(" ").append(pad(tableModel.getColumnName(c), widths[c])).append(" |");
            }
        } else {
            // Empty headers so the table still renders
            for (int c = 0; c < outputCols; c++)
                sb.append(" ").append(pad("", widths[c])).append(" |");
        }
        sb.append("\n");

        // Separator (required for markdown table recognition) with alignment
        sb.append("|");
        for (int c = 0; c < outputCols; c++) {
            Alignment align = c < columnAlignments.size() ? columnAlignments.get(c) : Alignment.NONE;
            // For row header column (first when useRowHeaders), skip alignment from the list
            Alignment colAlign;
            if (useRowHeaders && c == 0) {
                colAlign = Alignment.NONE;
            } else {
                int alignIdx = useRowHeaders ? c - 1 : c;
                colAlign = alignIdx < columnAlignments.size() ? columnAlignments.get(alignIdx) : Alignment.NONE;
            }
            String dashes = "-".repeat(widths[c]);
            String sep = switch (colAlign) {
                case LEFT -> ":" + dashes.substring(1);
                case RIGHT -> dashes.substring(1) + ":";
                case CENTER -> ":" + dashes.substring(2) + ":";
                default -> dashes;
            };
            sb.append(" ").append(sep).append(" |");
        }
        sb.append("\n");

        // Data rows
        for (int r = 0; r <= lastRow; r++) {
            sb.append("|");
            if (useRowHeaders) {
                String rh = r < rowHeaderValues.size() ? rowHeaderValues.get(r) : "";
                // Always output row header as bold
                if (!rh.isEmpty()) {
                    rh = "**" + rh + "**";
                }
                sb.append(" ").append(pad(rh, widths[0])).append(" |");
                for (int c = 0; c < cols; c++) {
                    Object val = tableModel.getValueAt(r, c);
                    String cell = val != null ? val.toString().trim() : "";
                    sb.append(" ").append(pad(cell, widths[c + 1])).append(" |");
                }
            } else {
                for (int c = 0; c < cols; c++) {
                    Object val = tableModel.getValueAt(r, c);
                    String cell = val != null ? val.toString().trim() : "";
                    sb.append(" ").append(pad(cell, widths[c])).append(" |");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String pad(String text, int width) {
        if (text.length() >= width) return text;
        return text + " ".repeat(width - text.length());
    }
}
