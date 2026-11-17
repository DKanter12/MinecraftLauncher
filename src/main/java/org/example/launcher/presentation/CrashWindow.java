package org.example.launcher.presentation;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.application.launch.CrashReport;
import org.example.launcher.i18n.Lang;

/**
 * РћРєРЅРѕ РѕС€РёР±РєРё Minecraft: РєРѕРґ Р·Р°РІРµСЂС€РµРЅРёСЏ, РєСЂР°С‚РєР°СЏ РїСЂРёС‡РёРЅР°,
 * РІР°Р¶РЅС‹Р№ С„СЂР°РіРјРµРЅС‚ Р»РѕРіР°, РїСЂРµРґРїРѕР»Р°РіР°РµРјС‹Р№ РёСЃС‚РѕС‡РЅРёРє РѕС€РёР±РєРё,
 * РєРЅРѕРїРєР° РѕС‚РєСЂС‹С‚РёСЏ РїРѕР»РЅРѕРіРѕ Р»РѕРіР° Рё Р·Р°РєСЂС‹С‚РёСЏ.
 */
public class CrashWindow extends Stage {

    public CrashWindow(Stage owner, CrashReport report) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(Lang.tr("launch.crash.title"));

        Label titleLabel = new Label(Lang.tr("launch.crash.title"));
        titleLabel.getStyleClass().add("error-title");

        Label codeLabel = new Label(
                Lang.tr("launch.crash.code", report.exitCode())
                        + report.reason());

        TextArea fragmentArea = new TextArea(report.logTail());
        fragmentArea.setEditable(false);
        fragmentArea.setWrapText(true);
        fragmentArea.setPrefWidth(560);
        fragmentArea.setPrefHeight(160);
        fragmentArea.getStyleClass().add("error-text");

        StringBuilder bottom = new StringBuilder();
        report.suggestion().ifPresent(suggestion -> {
            bottom.append("\n").append(suggestion);
        });
        Label suggestionLabel = new Label(bottom.toString());
        suggestionLabel.getStyleClass().add("account-hint");
        suggestionLabel.setWrapText(true);

        Button logButton = new Button(Lang.tr("button.openlog"));
        logButton.getStyleClass().add("browse-java-button");
        String fullLog = report.fullLog();
        boolean logExists = !fullLog.isBlank()
                && Files.isRegularFile(Paths.get(fullLog));
        logButton.setDisable(!logExists);
        logButton.setOnAction(e -> openLog(fullLog));

        Button closeButton = new Button(Lang.tr("button.close"));
        closeButton.getStyleClass().add("install-close-button");
        closeButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, logButton, closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(titleLabel, codeLabel, fragmentArea,
                suggestionLabel, buttons);
        VBox.setVgrow(fragmentArea, Priority.ALWAYS);

        Scene scene = new Scene(content, 620, 420);
        ThemeManager.apply(scene);
        setScene(scene);
        AppIcon.apply(this);
    }

    public static void show(Stage owner, CrashReport report) {
        CrashWindow window = new CrashWindow(owner, report);
        window.showAndWait();
    }

    private static void openLog(String fullLog) {
        try {
            Desktop.getDesktop().open(new File(fullLog));
        } catch (Exception ignored) {
            // С„Р°Р№Р» СѓР¶Рµ РїСЂРѕРІРµСЂРµРЅ РЅР° СЃСѓС‰РµСЃС‚РІРѕРІР°РЅРёРµ; РіРѕРЅРєР° РёРіРЅРѕСЂРёСЂСѓРµС‚СЃСЏ
        }
    }
}
