import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class javafxmysql extends Application {

    String url = "jdbc:mysql://localhost:3306/jyothi";
    String user = "root";
    String password = "connect123"; // Replace with your Oracle password

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Label message = new Label("SQL CRUD Operations (Mysql DB)");
        Button createBtn = new Button("Create Table");
        Button insertBtn = new Button("Insert Values");
        Button updateBtn = new Button("Update Table");
        Button deleteBtn = new Button("Delete Table");
        Button truncateBtn = new Button("Truncate Table");
        Button dropBtn = new Button("Drop Table");
        Button selectBtn = new Button("Select Records");

        createBtn.setOnAction(e -> showCreateTableWindow());
        insertBtn.setOnAction(e -> showInsertWindow());
        updateBtn.setOnAction(e -> showUpdateWindow());
        deleteBtn.setOnAction(e -> showSimpleTableActionWindow("Delete", "DELETE FROM "));
        truncateBtn.setOnAction(e -> showSimpleTableActionWindow("Truncate", "TRUNCATE TABLE "));
        dropBtn.setOnAction(e -> showSimpleTableActionWindow("Drop", "DROP TABLE "));
        selectBtn.setOnAction(e -> showSelectWindow());

        VBox root = new VBox(15, message, createBtn, insertBtn, updateBtn, deleteBtn, truncateBtn, dropBtn, selectBtn);
        root.setStyle("-fx-padding: 30; -fx-alignment: center;");
        Scene scene = new Scene(root, 400, 550);
        primaryStage.setScene(scene);
        primaryStage.setTitle("JavaFX Oracle DB Manager");
        primaryStage.show();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }

    public static class RowData {
        SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        List<SimpleStringProperty> values;

        RowData(List<SimpleStringProperty> values) {
            this.values = values;
        }
    }

    private List<String> getPrimaryKeyColumns(Connection conn, String tableName) throws SQLException {
        List<String> pkCols = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet pkRs = metaData.getPrimaryKeys(null, null, tableName)) {
            while (pkRs.next()) {
                pkCols.add(pkRs.getString("COLUMN_NAME"));
            }
        }
        return pkCols;
    }

    private void showSelectWindow() {
        Stage stage = new Stage();
        TextField tableField = new TextField();
        Button loadBtn = new Button("Load Table");

        VBox layout = new VBox(10, new Label("Enter Table Name:"), tableField, loadBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 300, 200));
        stage.setTitle("Select Records");
        stage.show();

        loadBtn.setOnAction(ev -> {
            String tableName = tableField.getText().trim().toUpperCase();
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name is required.");
                return;
            }

            Stage selectStage = new Stage();
            VBox contentLayout = new VBox(10);
            contentLayout.setStyle("-fx-padding: 10;");

            TableView<RowData> tableView = new TableView<>();
            tableView.setEditable(true);
            Button deleteSelectedBtn = new Button("Delete Selected Rows");
            contentLayout.getChildren().addAll(new ScrollPane(tableView), deleteSelectedBtn);

            List<String> columnNames = new ArrayList<>();
            int[] columnCount = {0};

            ObservableList<RowData> data = FXCollections.observableArrayList();

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {

                ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);
                ResultSetMetaData meta = rs.getMetaData();
                columnCount[0] = meta.getColumnCount();

                TableColumn<RowData, Boolean> selectCol = new TableColumn<>("Select");
                selectCol.setCellValueFactory(param -> param.getValue().selected);
                selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
                tableView.getColumns().add(selectCol);

                for (int i = 1; i <= columnCount[0]; i++) {
                    String colName = meta.getColumnName(i);
                    columnNames.add(colName);
                    final int colIndex = i - 1;
                    TableColumn<RowData, String> col = new TableColumn<>(colName);
                    col.setCellValueFactory(cellData -> cellData.getValue().values.get(colIndex));
                    tableView.getColumns().add(col);
                }

                while (rs.next()) {
                    List<SimpleStringProperty> rowValues = new ArrayList<>();
                    for (int i = 1; i <= columnCount[0]; i++) {
                        rowValues.add(new SimpleStringProperty(rs.getString(i)));
                    }
                    data.add(new RowData(rowValues));
                }

                tableView.setItems(data);

                // Fetch primary keys once here
                List<String> pkColumns = getPrimaryKeyColumns(conn, tableName);

                deleteSelectedBtn.setOnAction(e -> {
                    List<RowData> selectedRows = new ArrayList<>();
                    for (RowData row : data) {
                        if (row.selected.get()) selectedRows.add(row);
                    }

                    if (selectedRows.isEmpty()) {
                        showAlert(Alert.AlertType.WARNING, "No rows selected.");
                        return;
                    }

                    if (pkColumns.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "No primary key found for the table. Cannot safely delete rows.");
                        return;
                    }

                    try (Connection delConn = getConnection();
                         Statement delStmt = delConn.createStatement()) {

                        int deletedCount = 0;

                        for (RowData row : selectedRows) {
                            StringBuilder where = new StringBuilder();
                            for (int i = 0; i < columnCount[0]; i++) {
                                String column = columnNames.get(i);
                                if (!pkColumns.contains(column)) continue; // Only primary keys

                                String value = row.values.get(i).get();

                                if (where.length() > 0) where.append(" AND ");

                                if (value == null || value.equalsIgnoreCase("null") || value.isEmpty()) {
                                    where.append(column).append(" IS NULL");
                                } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                                    where.append(column).append("=").append(value);
                                } else {
                                    where.append(column).append("='").append(value.replace("'", "''")).append("'");
                                }
                            }

                            if (where.length() == 0) {
                                // No pk info - skip this row or handle accordingly
                                continue;
                            }

                            String delQuery = "DELETE FROM " + tableName + " WHERE " + where;
                            int affected = delStmt.executeUpdate(delQuery);
                            if (affected > 0) deletedCount++;
                        }

                        showAlert(Alert.AlertType.INFORMATION, deletedCount + " row(s) deleted.");
                        selectStage.close();

                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, ex.getMessage());
                    }
                });

            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
                return;
            }

            selectStage.setScene(new Scene(contentLayout, 800, 600));
            selectStage.setTitle("Table: " + tableName);
            selectStage.show();
        });
    }

    private void showCreateTableWindow() {
        Stage stage = new Stage();
        TextField tableNameField = new TextField();
        TextField fieldCountField = new TextField();
        Button nextBtn = new Button("Next");

        VBox layout = new VBox(10, new Label("Table Name:"), tableNameField,
                new Label("Number of Fields:"), fieldCountField, nextBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 300, 250));
        stage.setTitle("Create Table");
        stage.show();

        nextBtn.setOnAction(e -> {
            String tableName = tableNameField.getText().trim();
            int count;
            try {
                count = Integer.parseInt(fieldCountField.getText().trim());
            } catch (NumberFormatException ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid field count.");
                return;
            }
            showFieldCountWindow(stage, tableName, count);
        });
    }

    private void showFieldCountWindow(Stage parent, String tableName, int count) {
        Stage stage = new Stage();
        VBox fieldsBox = new VBox(10);
        List<TextField> names = new ArrayList<>();
        List<TextField> types = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            TextField name = new TextField();
            TextField type = new TextField();
            names.add(name);
            types.add(type);
            fieldsBox.getChildren().add(new HBox(10, new Label("Field " + (i + 1) + ":"), name, type));
        }

        Button createBtn = new Button("Create Table");
        fieldsBox.getChildren().add(createBtn);
        fieldsBox.setStyle("-fx-padding: 20;");
        stage.setScene(new Scene(fieldsBox, 400, 300));
        stage.setTitle("Field Details");
        stage.show();

        createBtn.setOnAction(e -> {
            StringBuilder sb = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
            for (int i = 0; i < count; i++) {
                sb.append(names.get(i).getText()).append(" ").append(types.get(i).getText());
                if (i < count - 1) sb.append(", ");
            }
            sb.append(")");
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sb.toString());
                showAlert(Alert.AlertType.INFORMATION, "Table created successfully.");
                stage.close();
                parent.close();
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });
    }

    private void showInsertWindow() {
        Stage stage = new Stage();
        TextField tableField = new TextField();
        Button loadBtn = new Button("Load Table Structure");

        VBox layout = new VBox(10, new Label("Table Name:"), tableField, loadBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 400, 150));
        stage.setTitle("Insert Values");
        stage.show();

        loadBtn.setOnAction(ev -> {
            String tableName = tableField.getText().trim().toUpperCase();
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name is required.");
                return;
            }

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE ROWNUM = 1")) {

                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                List<TextField> inputFields = new ArrayList<>();
                VBox formLayout = new VBox(10);
                formLayout.setStyle("-fx-padding: 20;");

                for (int i = 1; i <= columnCount; i++) {
                    String colName = meta.getColumnName(i);
                    String colType = meta.getColumnTypeName(i);
                    TextField input = new TextField();
                    input.setPromptText("Enter value for " + colName);
                    inputFields.add(input);

                    formLayout.getChildren().add(new HBox(10, new Label(colName + " (" + colType + "):"), input));
                }

                Button insertBtn = new Button("Insert");
                formLayout.getChildren().add(insertBtn);

                Stage insertStage = new Stage();
                insertStage.setScene(new Scene(new ScrollPane(formLayout), 450, 400));
                insertStage.setTitle("Insert into " + tableName);
                insertStage.show();

                insertBtn.setOnAction(e -> {
                    StringBuilder query = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
                    for (int i = 0; i < inputFields.size(); i++) {
                        String val = inputFields.get(i).getText().trim();
                        query.append("'").append(val.replace("'", "''")).append("'");
                        if (i < inputFields.size() - 1) query.append(", ");
                    }
                    query.append(")");

                    try (Statement insertStmt = conn.createStatement()) {
                        insertStmt.executeUpdate(query.toString());
                        showAlert(Alert.AlertType.INFORMATION, "Record inserted successfully.");
                        insertStage.close();
                        stage.close();
                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, ex.getMessage());
                    }
                });

            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });
    }

    private void showUpdateWindow() {
        // Similar to Insert, implement update record window if needed
        showAlert(Alert.AlertType.INFORMATION, "Update window not implemented yet.");
    }

    private void showSimpleTableActionWindow(String action, String sqlStart) {
        Stage stage = new Stage();
        TextField tableField = new TextField();
        Button executeBtn = new Button(action + " Table");

        VBox layout = new VBox(10, new Label("Enter Table Name:"), tableField, executeBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        stage.setScene(new Scene(layout, 300, 200));
        stage.setTitle(action + " Table");
        stage.show();

        executeBtn.setOnAction(e -> {
            String tableName = tableField.getText().trim().toUpperCase();
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Table name is required.");
                return;
            }

            String sql = sqlStart + tableName;
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
                showAlert(Alert.AlertType.INFORMATION, action + " successful.");
                stage.close();
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });
    }
}
