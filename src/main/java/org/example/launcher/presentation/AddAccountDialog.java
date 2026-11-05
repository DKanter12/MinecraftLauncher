package org.example.launcher.presentation;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.i18n.Lang;

/**
 * Единый диалог добавления аккаунта в стиле Glassmorphism.
 * Пользователь выбирает тип аккаунта двумя карточками-выбора
 * (Radio Cards): Офлайн или Ely.by, затем заполняет короткую форму.
 * <p>
 * Для Offline нужно только имя игрока; для Ely.by — логин + пароль.
 */
public class AddAccountDialog extends Stage {

    public enum Type { OFFLINE, ELY_BY }

    public record Result(Type type, String offlineName, String elyUser, String elyPass) {}

    private Result result;

    private final TextField offlineNameField = new TextField();
    private final TextField elyUserField = new TextField();
    private final PasswordField elyPassField = new PasswordField();
    private final Label errorLabel = new Label();
    private final Button actionButton = new Button(Lang.tr("account.create"));

    private Type selectedType = Type.OFFLINE;

    public AddAccountDialog(Stage owner) {
        // Прозрачный stage: окно рисуется само (overlay + карточка),
        // «backdrop-filter» эмулируется размытым снапшотом главного окна
        initStyle(StageStyle.TRANSPARENT);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        if (owner != null) {
            setWidth(owner.getWidth());
            setHeight(owner.getHeight());
            setX(owner.getX());
            setY(owner.getY());
        }

        // --- Шапка модального окна: заголовок + круглый крестик ---
        Label headerTitle = new Label(Lang.tr("account.title"));
        headerTitle.getStyleClass().add("modal-title");
        Button closeButton = new Button("\u2715");
        closeButton.getStyleClass().add("modal-close");
        closeButton.setOnAction(e -> close());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(12, headerTitle, headerSpacer,
                closeButton);
        headerRow.setAlignment(Pos.CENTER_RIGHT);

        // --- Карточки-выбора типа (Radio Cards) ---
        ToggleGroup typeGroup = new ToggleGroup();
        ToggleButton offlineToggle = typeCard(typeGroup, "\uD83D\uDC64",
                Lang.tr("account.card.offline.title"),
                Lang.tr("account.card.offline.sub"));
        offlineToggle.setSelected(true);
        ToggleButton elyToggle = typeCard(typeGroup, "\uD83E\uDD8A",
                Lang.tr("account.card.ely.title"),
                Lang.tr("account.card.ely.sub"));
        HBox typeRow = new HBox(8, offlineToggle, elyToggle);
        typeRow.setAlignment(Pos.CENTER_LEFT);

        // --- Панель Offline ---
        Label offlineNameTitle = new Label(Lang.tr("account.name"));
        offlineNameTitle.getStyleClass().add("section-title");
        offlineNameField.setPromptText(Lang.tr("account.name.prompt"));
        offlineNameField.getStyleClass().add("modal-input");
        offlineNameField.setPrefHeight(44);
        offlineNameField.setMaxWidth(Double.MAX_VALUE);
        Label offlineHint = new Label(Lang.tr("account.name.hint"));
        offlineHint.getStyleClass().add("account-card-hint");
        VBox offlineBox = new VBox(6, offlineNameTitle,
                offlineNameField, offlineHint);

        // --- Панель Ely.by ---
        Label elyTitle = new Label(Lang.tr("account.ely"));
        elyTitle.getStyleClass().add("section-title");
        elyUserField.setPromptText(Lang.tr("account.user.prompt"));
        elyUserField.getStyleClass().add("modal-input");
        elyUserField.setPrefHeight(44);
        elyUserField.setMaxWidth(Double.MAX_VALUE);
        elyPassField.setPromptText(Lang.tr("server.password"));
        elyPassField.getStyleClass().add("modal-input");
        elyPassField.setPrefHeight(44);
        elyPassField.setMaxWidth(Double.MAX_VALUE);
        Label elyHint = new Label(Lang.tr("account.ely.hint"));
        elyHint.getStyleClass().add("account-card-hint");
        elyHint.setWrapText(true);
        VBox elyBox = new VBox(6, elyTitle, elyUserField, elyPassField,
                elyHint);
        elyBox.setVisible(false);
        elyBox.setManaged(false);

        // Логика переключения карточек
        typeGroup.selectedToggleProperty().addListener((obs, old, val) -> {
            boolean isOffline = val == offlineToggle;
            if (val == null) {
                offlineToggle.setSelected(true);
                return;
            }
            selectedType = isOffline ? Type.OFFLINE : Type.ELY_BY;
            offlineBox.setVisible(isOffline);
            offlineBox.setManaged(isOffline);
            elyBox.setVisible(!isOffline);
            elyBox.setManaged(!isOffline);
            actionButton.setText(isOffline ? Lang.tr("account.create")
                    : Lang.tr("account.login"));
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
        });

        errorLabel.getStyleClass().add("account-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // --- Footer: одинаковая ширина, прижаты вправо ---
        actionButton.getStyleClass().add("modal-button-primary");
        actionButton.setDefaultButton(true);
        actionButton.setDisable(true);
        actionButton.setPrefWidth(140);
        actionButton.setMinWidth(Region.USE_PREF_SIZE);
        actionButton.setOnAction(e -> onAction());

        Button cancelButton = new Button(Lang.tr("button.cancel"));
        cancelButton.getStyleClass().add("modal-button-ghost");
        cancelButton.setCancelButton(true);
        cancelButton.setPrefWidth(140);
        cancelButton.setMinWidth(Region.USE_PREF_SIZE);
        cancelButton.setOnAction(e -> close());

        offlineNameField.textProperty().addListener((obs, o, v) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
        });
        elyUserField.textProperty().addListener((obs, o, v) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
        });
        elyPassField.textProperty().addListener((obs, o, v) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
        });

        HBox buttons = new HBox(8, cancelButton, actionButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(16, 0, 0, 0));

        VBox content = new VBox(12, headerRow, typeRow, offlineBox,
                elyBox, errorLabel, buttons);
        content.setPrefWidth(420);

        VBox card = new VBox(content);
        card.getStyleClass().add("modal-card");
        card.setMinHeight(340);
        card.setMaxWidth(420);

        // -- Glassmorphism backdrop (общий с NewInstanceDialog);
        // карточка центрируется и сохраняет собственный размер --
        StackPane overlay = new StackPane();
        NewInstanceDialog.addBlurBackdrop(overlay, owner);
        HBox centered = new HBox(card);
        centered.setAlignment(Pos.CENTER);
        overlay.getChildren().add(centered);

        Scene scene = new Scene(overlay, owner != null ? owner.getWidth()
                : 480, owner != null ? owner.getHeight() : 420);
        scene.setFill(Color.TRANSPARENT);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        setScene(scene);

        updateActionButton();
    }

    /** Карточка-выбор (Radio Card): иконка-бейдж + название + подсказка. */
    private static ToggleButton typeCard(ToggleGroup group, String glyph,
                                         String title, String subtitle) {
        Label icon = new Label(glyph);
        icon.getStyleClass().add("account-card-icon");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("account-card-title");

        Label subLbl = new Label(subtitle);
        subLbl.getStyleClass().add("account-card-subtitle");
        subLbl.setWrapText(true);

        VBox text = new VBox(2, titleLbl, subLbl);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(10, icon, text);
        row.setAlignment(Pos.CENTER_LEFT);

        ToggleButton card = new ToggleButton();
        card.setGraphic(row);
        card.setText("");
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("account-card");
        card.setToggleGroup(group);
        return card;
    }

    private void updateActionButton() {
        if (selectedType == Type.OFFLINE) {
            String name = offlineNameField.getText() == null ? "" : offlineNameField.getText().trim();
            actionButton.setDisable(name.isEmpty());
        } else {
            String u = elyUserField.getText() == null ? "" : elyUserField.getText().trim();
            String p = elyPassField.getText() == null ? "" : elyPassField.getText();
            actionButton.setDisable(u.isEmpty() || p.isEmpty());
        }
    }

    private void onAction() {
        if (selectedType == Type.OFFLINE) {
            String name = offlineNameField.getText().trim();
            if (name.isEmpty()) {
                showError(Lang.tr("account.error.empty"));
                return;
            }
            if (name.length() > 16) {
                showError(Lang.tr("account.error.long"));
                return;
            }
            if (!name.matches("[a-zA-Z0-9_]+")) {
                showError(Lang.tr("account.error.chars"));
                return;
            }
            result = new Result(Type.OFFLINE, name, null, null);
            close();
        } else {
            String u = elyUserField.getText().trim();
            String p = elyPassField.getText();
            if (u.isEmpty()) {
                showError(Lang.tr("account.error.user"));
                return;
            }
            if (p.isEmpty()) {
                showError(Lang.tr("account.error.pass"));
                return;
            }
            result = new Result(Type.ELY_BY, null, u, p);
            close();
        }
    }

    private void showError(String text) {
        errorLabel.setText(text);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public Result getResult() { return result; }

    public static Result showDialog(Stage owner) {
        AddAccountDialog d = new AddAccountDialog(owner);
        d.showAndWait();
        return d.getResult();
    }
}
