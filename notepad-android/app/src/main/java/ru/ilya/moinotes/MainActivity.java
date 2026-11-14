package ru.ilya.moinotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.PopupMenu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(245, 246, 250);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(31, 39, 54);
    private static final int MUTED = Color.rgb(126, 135, 151);
    private static final int BLUE = Color.rgb(53, 104, 212);

    private final ArrayList<Note> notes = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;

    private LinearLayout page;
    private LinearLayout notesList;
    private EditText searchField;
    private EditText titleField;
    private EditText bodyField;
    private long editingId = -1L;
    private Runnable pendingSave;

    private static class Note {
        long id;
        String title;
        String body;
        long createdAt;
        long updatedAt;
        boolean pinned;

        Note(long id) {
            this.id = id;
            this.title = "";
            this.body = "";
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        preferences = getSharedPreferences("notes_storage", MODE_PRIVATE);
        loadNotes();
        showList();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    private void showList() {
        editingId = -1L;
        page = vertical();
        page.setBackgroundColor(BG);
        page.setPadding(dp(20), dp(18), dp(20), dp(12));

        LinearLayout header = horizontal();
        TextView heading = label("Мои заметки", 27, TEXT, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView add = label("+", 30, Color.WHITE, false);
        add.setGravity(Gravity.CENTER);
        add.setBackground(rounded(BLUE, 16));
        header.addView(add, lp(52, 48));
        add.setOnClickListener(v -> createNote());

        page.addView(header, lp(-1, 52));

        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
        searchField.setTextColor(TEXT);
        searchField.setHintTextColor(MUTED);
        searchField.setHint("Поиск по заметкам");
        searchField.setPadding(dp(16), 0, dp(16), 0);
        searchField.setBackground(rounded(CARD, 14));
        LinearLayout.LayoutParams searchParams = lp(-1, 50);
        searchParams.topMargin = dp(18);
        page.addView(searchField, searchParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        notesList = vertical();
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(18);
        notesList.setPadding(0, 0, 0, dp(10));
        scroll.addView(notesList, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView footer = label("Записи хранятся только на этом телефоне", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = lp(-1, 30);
        footerParams.topMargin = dp(4);
        page.addView(footer, footerParams);

        setContentView(page);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderNotes(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        renderNotes();
    }

    private void renderNotes() {
        if (notesList == null) return;
        notesList.removeAllViews();

        String query = searchField == null ? "" : searchField.getText().toString().trim().toLowerCase(new Locale("ru"));
        ArrayList<Note> visible = new ArrayList<>();
        for (Note note : notes) {
            String haystack = (note.title + " " + note.body).toLowerCase(new Locale("ru"));
            if (query.isEmpty() || haystack.contains(query)) visible.add(note);
        }
        Collections.sort(visible, new Comparator<Note>() {
            @Override public int compare(Note a, Note b) {
                if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });

        if (visible.isEmpty()) {
            TextView empty = label(query.isEmpty() ? "Пока нет заметок\nНажмите +, чтобы создать первую" : "Ничего не найдено", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(5), 1f);
            LinearLayout.LayoutParams p = lp(-1, 190);
            notesList.addView(empty, p);
            return;
        }

        for (Note note : visible) {
            LinearLayout card = vertical();
            card.setPadding(dp(16), dp(14), dp(12), dp(13));
            card.setBackground(rounded(CARD, 16));
            LinearLayout.LayoutParams cardParams = lp(-1, -2);
            cardParams.bottomMargin = dp(10);
            notesList.addView(card, cardParams);

            LinearLayout top = horizontal();
            String title = note.title.trim().isEmpty() ? "Без названия" : note.title.trim();
            TextView titleView = label((note.pinned ? "★  " : "") + title, 17, TEXT, true);
            titleView.setMaxLines(1);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView menu = label("⋮", 24, MUTED, true);
            menu.setGravity(Gravity.CENTER);
            top.addView(menu, lp(34, 30));
            menu.setOnClickListener(v -> showNoteMenu(menu, note));
            card.addView(top);

            if (!note.body.trim().isEmpty()) {
                TextView preview = label(note.body.trim().replace('\n', ' '), 14, MUTED, false);
                preview.setMaxLines(2);
                preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams previewParams = lp(-1, -2);
                previewParams.topMargin = dp(7);
                card.addView(preview, previewParams);
            }

            String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, new Locale("ru"))
                    .format(new Date(note.updatedAt));
            TextView dateView = label(date, 11, MUTED, false);
            LinearLayout.LayoutParams dateParams = lp(-1, -2);
            dateParams.topMargin = dp(10);
            card.addView(dateView, dateParams);

            card.setOnClickListener(v -> openNote(note.id));
        }
    }

    private void showNoteMenu(View anchor, Note note) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(note.pinned ? "Открепить" : "Закрепить");
        menu.getMenu().add("Удалить");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Удалить".equals(title)) confirmDelete(note.id);
            else {
                note.pinned = !note.pinned;
                note.updatedAt = System.currentTimeMillis();
                saveNotes();
                renderNotes();
            }
            return true;
        });
        menu.show();
    }

    private void createNote() {
        Note note = new Note(System.currentTimeMillis());
        notes.add(note);
        saveNotes();
        openNote(note.id);
    }

    private Note findNote(long id) {
        for (Note note : notes) if (note.id == id) return note;
        return null;
    }

    private void openNote(long id) {
        Note note = findNote(id);
        if (note == null) return;
        editingId = id;

        page = vertical();
        page.setBackgroundColor(BG);
        page.setPadding(dp(18), dp(14), dp(18), dp(12));

        LinearLayout header = horizontal();
        TextView back = label("‹", 34, BLUE, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, lp(42, 46));
        back.setOnClickListener(v -> closeEditor());

        TextView heading = label("Заметка", 19, TEXT, true);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView save = label("Готово", 15, BLUE, true);
        save.setGravity(Gravity.CENTER);
        header.addView(save, lp(66, 44));
        save.setOnClickListener(v -> closeEditor());
        page.addView(header, lp(-1, 48));

        titleField = new EditText(this);
        titleField.setSingleLine(true);
        titleField.setTextSize(21);
        titleField.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleField.setTextColor(TEXT);
        titleField.setHintTextColor(MUTED);
        titleField.setHint("Название");
        titleField.setPadding(dp(14), dp(12), dp(14), dp(12));
        titleField.setBackground(rounded(CARD, 14));
        LinearLayout.LayoutParams titleParams = lp(-1, 58);
        titleParams.topMargin = dp(18);
        page.addView(titleField, titleParams);

        bodyField = new EditText(this);
        bodyField.setTextSize(17);
        bodyField.setTextColor(TEXT);
        bodyField.setHintTextColor(MUTED);
        bodyField.setHint("Начните писать...");
        bodyField.setGravity(Gravity.TOP | Gravity.START);
        bodyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bodyField.setPadding(dp(15), dp(15), dp(15), dp(15));
        bodyField.setBackground(rounded(CARD, 14));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyParams.topMargin = dp(12);
        bodyParams.bottomMargin = dp(8);
        page.addView(bodyField, bodyParams);

        TextView info = label("Сохраняется автоматически", 12, MUTED, false);
        info.setGravity(Gravity.CENTER);
        page.addView(info, lp(-1, 28));

        titleField.setText(note.title);
        bodyField.setText(note.body);
        setContentView(page);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleSave(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        titleField.addTextChangedListener(watcher);
        bodyField.addTextChangedListener(watcher);
    }

    private void scheduleSave() {
        if (pendingSave != null) handler.removeCallbacks(pendingSave);
        pendingSave = () -> saveEditor();
        handler.postDelayed(pendingSave, 450);
    }

    private void saveEditor() {
        if (editingId < 0 || titleField == null || bodyField == null) return;
        Note note = findNote(editingId);
        if (note == null) return;
        note.title = titleField.getText().toString();
        note.body = bodyField.getText().toString();
        note.updatedAt = System.currentTimeMillis();
        saveNotes();
    }

    private void closeEditor() {
        if (pendingSave != null) {
            handler.removeCallbacks(pendingSave);
            pendingSave = null;
        }
        saveEditor();
        Note note = findNote(editingId);
        if (note != null && note.title.trim().isEmpty() && note.body.trim().isEmpty()) {
            notes.remove(note);
            saveNotes();
        }
        titleField = null;
        bodyField = null;
        editingId = -1L;
        showList();
    }

    private void confirmDelete(long id) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить заметку?")
                .setMessage("Эту запись нельзя будет восстановить.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    Note note = findNote(id);
                    if (note != null) notes.remove(note);
                    saveNotes();
                    renderNotes();
                })
                .show();
    }

    private void loadNotes() {
        notes.clear();
        String raw = preferences.getString("notes", "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Note note = new Note(object.optLong("id", System.currentTimeMillis() + i));
                note.title = object.optString("title", "");
                note.body = object.optString("body", "");
                note.createdAt = object.optLong("createdAt", note.createdAt);
                note.updatedAt = object.optLong("updatedAt", note.createdAt);
                note.pinned = object.optBoolean("pinned", false);
                notes.add(note);
            }
        } catch (Exception ignored) {
            notes.clear();
        }
    }

    private void saveNotes() {
        JSONArray array = new JSONArray();
        try {
            for (Note note : notes) {
                JSONObject object = new JSONObject();
                object.put("id", note.id);
                object.put("title", note.title);
                object.put("body", note.body);
                object.put("createdAt", note.createdAt);
                object.put("updatedAt", note.updatedAt);
                object.put("pinned", note.pinned);
                array.put(object);
            }
        } catch (Exception ignored) {}
        preferences.edit().putString("notes", array.toString()).apply();
    }

    @Override
    public void onBackPressed() {
        if (editingId >= 0) closeEditor();
        else super.onBackPressed();
    }
}
