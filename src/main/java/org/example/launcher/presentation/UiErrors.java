package org.example.launcher.presentation;

import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Единый формат ошибки в UI: строка статуса + диалог.
 * Раньше пары {@code statusLabel.setText + ErrorDialog.show}
 * были расписаны вручную в десятке мест {@code MainView} —
 * теперь одним вызовом, тексты не расходятся.
 */
public final class UiErrors {

    private UiErrors() {
    }

    /** Статус + модальный диалог с {@code Class: message} причины. */
    public static void fail(Label statusLabel, Stage owner,
                            String statusText, String title, Throwable cause) {
        String detail = cause == null
                ? ""
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        fail(statusLabel, owner, statusText, title, detail);
    }

    /** Статус + модальный диалог с готовым текстом. */
    public static void fail(Label statusLabel, Stage owner,
                            String statusText, String title, String detail) {
        if (statusLabel != null) {
            statusLabel.setText(statusText);
        }
        if (owner != null) {
            ErrorDialog.show(owner, title, detail);
        }
    }
}
