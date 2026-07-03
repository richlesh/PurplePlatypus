/*
 * (c) 2026 The Boeing Company
 */

/**
 * TableDialog.java
 *
 * A modal dialog for inserting or editing a Markdown table. Provides a mini
 * spreadsheet (JTable) where users can enter table contents including editable
 * column and row headers. Checkboxes control whether headers are included in output.
 */
package com.glowingcat;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A modal dialog with a spreadsheet-style table editor for creating and
 * editing Markdown tables, with optional row and column headers.
 */
public class TableDialog extends JDialog {

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JCheckBox columnHeadersBox;
    private final JCheckBox rowHeadersBox;
    private boolean confirmed = false;

    private static final int DEFAULT_ROWS = 4;
    private static final int DEFAULT_COLS = 3;

    /**
     * Creates the Table dialog. If tableText contains a valid markdown table,
     * it is parsed and displayed in the spreadsheet.
     *
     * @param owner     the parent frame
     * @param tableText existing markdown table text to parse, or null for a blank table
     */
    public TableDialog(JFrame owner, String tableText) {
        super(owner, "Insert Table", true);

        // Parse existing table or create empty model
        String[][] data;
        String[] colHeaders;
        String[] rowHeaders = null;
        boolean hasColumnHeaders = true;
        boolean hasRowHeaders = false;

        if (tableText != null && tableText.contains("|")) {
            List<String[]> parsed = parseMarkdownTable(tableText);
            if (parsed.size() > 1) {
                colHeaders = parsed.get(0);
                // Detect if first column looks like row headers (first cell of header is empty or "")
                // We'll include row headers support but default to off for parsed tables
                int dataCols = colHeaders.length;
                int dataRows = parsed.size() - 1;
                data = new String[Math.max(dataRows, DEFAULT_ROWS)][dataCols];
                for (int i = 1; i < parsed.size(); i++) {
                    String[] row = parsed.get(i);
                    for (int c = 0; c < dataCols; c++) {
                        data[i - 1][c] = c < row.length ? row[c] : "";
                    }
                }
                // Fill remaining rows
                for (int i = dataRows; i < data.length; i++) {
                    data[i] = new String[dataCols];
                    for (int j = 0; j < dataCols; j++) {
                        data[i][j] = "";
                    }
                }
            } else if (parsed.size() == 1) {
                colHeaders = parsed.get(0);
                data = createEmptyData(DEFAULT_ROWS, colHeaders.length);
            } else {
                colHeaders = createDefaultHeaders(DEFAULT_COLS);
                data = createEmptyData(DEFAULT_ROWS, DEFAULT_COLS);
            }
        } else {
            colHeaders = createDefaultHeaders(DEFAULT_COLS);
            data = createEmptyData(DEFAULT_ROWS, DEFAULT_COLS);
        }

        tableModel = new DefaultTableModel(data, colHeaders);
        table = new JTable(tableModel);
        table.setCellSelectionEnabled(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);

        // Make column headers editable by double-clicking
        JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int col = header.columnAtPoint(e.getPoint());
                    if (col >= 0) {
                        editColumnHeader(col);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(550, 220));

        // Options panel with checkboxes
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        columnHeadersBox = new JCheckBox("Column Headers", hasColumnHeaders);
        rowHeadersBox = new JCheckBox("Row Headers", hasRowHeaders);
        optionsPanel.add(columnHeadersBox);
        optionsPanel.add(rowHeadersBox);

        // Control buttons for adding/removing rows and columns
        JPanel controlPanel = new JPanel(new BorderLayout());
        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addRowBtn = new JButton("+ Row");
        JButton removeRowBtn = new JButton("- Row");
        JButton addColBtn = new JButton("+ Column");
        JButton removeColBtn = new JButton("- Column");

        addRowBtn.addActionListener(e -> {
            tableModel.addRow(new String[tableModel.getColumnCount()]);
        });
        removeRowBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && tableModel.getRowCount() > 1) {
                tableModel.removeRow(row);
            }
        });
        addColBtn.addActionListener(e -> {
            int colCount = tableModel.getColumnCount();
            tableModel.addColumn("Col " + (colCount + 1));
        });
        removeColBtn.addActionListener(e -> {
            int colCount = tableModel.getColumnCount();
            if (colCount > 1) {
                int rows = tableModel.getRowCount();
                String[] newHeaders = new String[colCount - 1];
                for (int i = 0; i < colCount - 1; i++) {
                    newHeaders[i] = tableModel.getColumnName(i);
                }
                String[][] newData = new String[rows][colCount - 1];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < colCount - 1; c++) {
                        Object val = tableModel.getValueAt(r, c);
                        newData[r][c] = val != null ? val.toString() : "";
                    }
                }
                tableModel.setDataVector(newData, newHeaders);
            }
        });

        buttonsRow.add(addRowBtn);
        buttonsRow.add(removeRowBtn);
        buttonsRow.add(addColBtn);
        buttonsRow.add(removeColBtn);

        controlPanel.add(optionsPanel, BorderLayout.NORTH);
        controlPanel.add(buttonsRow, BorderLayout.SOUTH);

        // Hint label
        JLabel hintLabel = new JLabel("Double-click a column header to edit it.");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        hintLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));

        // Dialog buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            confirmed = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Layout
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

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

    /**
     * Shows a dialog to edit a column header name.
     */
    private void editColumnHeader(int col) {
        String current = tableModel.getColumnName(col);
        String newName = JOptionPane.showInputDialog(this, "Column header:", current);
        if (newName != null) {
            // Rebuild column identifiers to rename the column
            int colCount = tableModel.getColumnCount();
            String[] headers = new String[colCount];
            for (int i = 0; i < colCount; i++) {
                headers[i] = (i == col) ? newName : tableModel.getColumnName(i);
            }
            // Save data, rebuild model
            int rows = tableModel.getRowCount();
            String[][] data = new String[rows][colCount];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < colCount; c++) {
                    Object val = tableModel.getValueAt(r, c);
                    data[r][c] = val != null ? val.toString() : "";
                }
            }
            tableModel.setDataVector(data, headers);
        }
    }

    /**
     * Parses a markdown table string into a list of row arrays.
     * The first row is treated as headers. Separator rows are skipped.
     */
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
            for (int i = 0; i < cells.length; i++) {
                cells[i] = cells[i].trim();
            }
            rows.add(cells);
        }

        // Normalize column count
        int maxCols = 0;
        for (String[] row : rows) {
            maxCols = Math.max(maxCols, row.length);
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).length < maxCols) {
                String[] padded = new String[maxCols];
                System.arraycopy(rows.get(i), 0, padded, 0, rows.get(i).length);
                for (int j = rows.get(i).length; j < maxCols; j++) {
                    padded[j] = "";
                }
                rows.set(i, padded);
            }
        }

        return rows;
    }

    private boolean isSeparatorRow(String line) {
        String stripped = line.replace("|", "").replace("-", "")
                .replace(":", "").replace(" ", "");
        return stripped.isEmpty();
    }

    private String[] createDefaultHeaders(int cols) {
        String[] headers = new String[cols];
        for (int i = 0; i < cols; i++) {
            headers[i] = "Col " + (i + 1);
        }
        return headers;
    }

    private String[][] createEmptyData(int rows, int cols) {
        String[][] data = new String[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                data[r][c] = "";
            }
        }
        return data;
    }

    /** Returns whether the user clicked Save. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Generates a markdown table from the current spreadsheet contents.
     * Respects the Column Headers and Row Headers checkbox settings.
     * When Row Headers is enabled, the first column of each data row is treated
     * as a row header (rendered bold in the first cell).
     */
    public String getMarkdownTable() {
        int cols = tableModel.getColumnCount();
        int rows = tableModel.getRowCount();
        boolean useColHeaders = columnHeadersBox.isSelected();
        boolean useRowHeaders = rowHeadersBox.isSelected();

        // Find the last non-empty row
        int lastRow = -1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Object val = tableModel.getValueAt(r, c);
                if (val != null && !val.toString().trim().isEmpty()) {
                    lastRow = r;
                    break;
                }
            }
        }
        if (lastRow < 0 && !useColHeaders) return "";

        // Calculate column widths for alignment
        int[] widths = new int[cols];
        if (useColHeaders) {
            for (int c = 0; c < cols; c++) {
                widths[c] = Math.max(tableModel.getColumnName(c).length(), 3);
            }
        } else {
            for (int c = 0; c < cols; c++) {
                widths[c] = 3;
            }
        }
        for (int r = 0; r <= lastRow; r++) {
            for (int c = 0; c < cols; c++) {
                Object val = tableModel.getValueAt(r, c);
                String cell = val != null ? val.toString().trim() : "";
                // If row headers, first col gets bold markers
                if (useRowHeaders && c == 0 && !cell.isEmpty()) {
                    widths[c] = Math.max(widths[c], cell.length() + 4); // account for **bold**
                } else {
                    widths[c] = Math.max(widths[c], cell.length());
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        if (useColHeaders) {
            // Header row
            sb.append("|");
            for (int c = 0; c < cols; c++) {
                sb.append(" ").append(pad(tableModel.getColumnName(c), widths[c])).append(" |");
            }
            sb.append("\n");

            // Separator row
            sb.append("|");
            for (int c = 0; c < cols; c++) {
                sb.append(" ").append("-".repeat(widths[c])).append(" |");
            }
            sb.append("\n");
        }

        // Data rows
        for (int r = 0; r <= lastRow; r++) {
            sb.append("|");
            for (int c = 0; c < cols; c++) {
                Object val = tableModel.getValueAt(r, c);
                String cell = val != null ? val.toString().trim() : "";
                // Bold the first column if row headers enabled
                if (useRowHeaders && c == 0 && !cell.isEmpty()) {
                    cell = "**" + cell + "**";
                }
                sb.append(" ").append(pad(cell, widths[c])).append(" |");
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
