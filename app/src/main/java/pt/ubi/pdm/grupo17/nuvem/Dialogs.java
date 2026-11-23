package pt.ubi.pdm.grupo17.nuvem;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Consumer;

/** Diálogos com o estilo da app (só apresentação). */
final class Dialogs {
    private Dialogs() { }

    /** Construtor com título e ícone na cor primária. */
    static MaterialAlertDialogBuilder builder(Context c, CharSequence title, @DrawableRes int icon) {
        Drawable d = AppCompatResources.getDrawable(c, icon).mutate();
        d.setTint(ContextCompat.getColor(c, R.color.g17_icon));
        return new MaterialAlertDialogBuilder(c).setTitle(title).setIcon(d);
    }

    /** Diálogo com um campo de texto; o botão positivo só fica ativo com texto. */
    static void input(Activity a, @StringRes int title, @DrawableRes int icon, @StringRes int hint,
                      int inputType, @StringRes int helper, @StringRes int ok, Consumer<String> onOk) {
        View v = a.getLayoutInflater().inflate(R.layout.dialog_input, null);
        TextInputLayout layout = v.findViewById(R.id.inputLayout);
        TextInputEditText field = v.findViewById(R.id.input);
        layout.setHint(a.getString(hint));
        layout.setStartIconDrawable(icon);
        if (helper != 0) layout.setHelperText(a.getString(helper));
        field.setInputType(inputType);

        AlertDialog dialog = builder(a, a.getString(title), icon)
                .setView(v)
                .setPositiveButton(ok, (d, w) -> onOk.accept(String.valueOf(field.getText()).trim()))
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            field.requestFocus();
            field.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    positive.setEnabled(!s.toString().trim().isEmpty());
                }
            });
        });
        dialog.show();
    }
}
