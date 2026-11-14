package ru.obyavleniya.poisk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;
import java.util.Locale;
import java.net.URI;

/** Simple unofficial Avito search companion. Never pretends to retrieve sales statistics. */
public class MainActivity extends Activity {
    private static final int INK = Color.rgb(15,23,42);
    private static final int BLUE = Color.rgb(37,99,235);
    private static final String FAVORITES = "favorite_urls";
    private SharedPreferences prefs;
    private final LinearLayout[] pages = new LinearLayout[3];
    private final Button[] tabs = new Button[3];
    private EditText query, min, max, purchase, sale, shipping, percent, extras;
    private WebView web;
    private TextView status, calculation;
    private LinearLayout savedItems;
    private String currentUrl = "";
    private int tab = 0;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        prefs = getSharedPreferences("search_local", MODE_PRIVATE);
        LinearLayout root = vertical();
        root.setBackgroundColor(Color.rgb(246,248,252));
        setContentView(root);
        TextView header = label("ПОИСК ОБЪЯВЛЕНИЙ", 21);
        header.setTextColor(Color.WHITE);
        header.setPadding(dp(16), dp(16), dp(16), dp(16));
        header.setBackgroundColor(INK);
        root.addView(header);
        LinearLayout menu = horizontal();
        String[] names = {"Поиск", "Избранное", "Прибыль"};
        for (int i=0; i<3; i++) {
            final int destination = i;
            tabs[i] = action(names[i]);
            menu.addView(tabs[i], new LinearLayout.LayoutParams(0,dp(49),1));
            tabs[i].setOnClickListener(v -> switchTo(destination));
        }
        root.addView(menu);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1,0,1));
        for (int i=0;i<3;i++) {
            pages[i] = vertical();
            content.addView(pages[i], new FrameLayout.LayoutParams(-1,-1));
        }
        createSearch();
        createSaved();
        createCalculator();
        switchTo(0);
    }

    private void createSearch() {
        LinearLayout controls = vertical();
        controls.setPadding(dp(10),dp(9),dp(10),dp(6));
        pages[0].addView(controls);
        query = field("Что ищем? Например, сварочный аппарат",false);
        query.setText(prefs.getString("lastQuery",""));
        controls.addView(query,new LinearLayout.LayoutParams(-1,dp(49)));
        LinearLayout filters = horizontal();
        min = field("Цена от, ₽",true);
        max = field("Цена до, ₽",true);
        min.setText(prefs.getString("min",""));
        max.setText(prefs.getString("max",""));
        filters.addView(min,new LinearLayout.LayoutParams(0,dp(49),1));
        filters.addView(max,new LinearLayout.LayoutParams(0,dp(49),1));
        controls.addView(filters);
        LinearLayout buttons = horizontal();
        Button find = action("Найти");
        find.setTextColor(Color.WHITE);
        find.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        find.setOnClickListener(v -> search(false));
        Button open = action("В браузере");
        open.setOnClickListener(v -> search(true));
        buttons.addView(find,new LinearLayout.LayoutParams(0,dp(49),1));
        buttons.addView(open,new LinearLayout.LayoutParams(0,dp(49),1));
        controls.addView(buttons);
        LinearLayout more = horizontal();
        status = label("Поиск настоящих страниц Авито по всей России",12);
        status.setPadding(dp(8),dp(12),dp(4),0);
        more.addView(status,new LinearLayout.LayoutParams(0,dp(50),1));
        Button save = action("☆ Сохранить");
        save.setOnClickListener(v -> saveCurrent());
        more.addView(save);
        pages[0].addView(more);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String target = request.getUrl().toString();
                if (isAvito(target)) return false;
                // Do not open arbitrary non-Avito pages inside this embedded WebView.
                if (request.isForMainFrame()) status.setText("Внешняя страница заблокирована");
                return true;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if (isAvito(url)) {
                    currentUrl = url;
                    status.setText("Авито · объявления с сайта");
                }
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                if (request.isForMainFrame()) status.setText("Ошибка загрузки — используйте «В браузере»");
            }
        });
        pages[0].addView(web,new LinearLayout.LayoutParams(-1,0,1));
        web.loadDataWithBaseURL(null,
          "<html lang='ru'><meta name='viewport' content='width=device-width, initial-scale=1'>" +
          "<body style='font-family:sans-serif;padding:22px;color:#334155;line-height:1.7'>" +
          "<h2>Поиск по всей России</h2>" +
          "<p>Введите товар и нажмите <b>Найти</b>. Содержимое загрузится напрямую с сайта Авито.</p>" +
          "<p>Если сайт недоступен внутри приложения, нажмите <b>В браузере</b>.</p>" +
          "</body></html>","text/html","UTF-8",null);
    }

    private void search(boolean external) {
        String text = query.getText().toString().trim();
        String lo = min.getText().toString().trim();
        String hi = max.getText().toString().trim();
        if (text.isEmpty()) { query.setError("Введите название товара"); return; }
        if ((!lo.isEmpty()&&!lo.matches("[0-9]+")) || (!hi.isEmpty()&&!hi.matches("[0-9]+"))) {
            toast("Цены вводятся только цифрами"); return;
        }
        if (!lo.isEmpty()&&!hi.isEmpty()&&new BigDecimal(lo).compareTo(new BigDecimal(hi))>0) {
            toast("Цена от больше цены до"); return;
        }
        String encoded;
        try { encoded = URLEncoder.encode(text,"UTF-8").replace("+","%20"); }
        catch (Exception e) { toast("Ошибка поискового запроса"); return; }
        String url = "https://www.avito.ru/rossiya?q="+encoded+
          (lo.isEmpty()?"":"&pmin="+lo)+(hi.isEmpty()?"":"&pmax="+hi);
        prefs.edit().putString("lastQuery",text).putString("min",lo).putString("max",hi).apply();
        currentUrl = url;
        if (external) launchBrowser(url);
        else load(url);
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager keyboard=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(),0);
        }
    }

    private static boolean isAvito(String url) {
        if (url==null) return false;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null &&
              ("avito.ru".equalsIgnoreCase(host) || host.toLowerCase(Locale.ROOT).endsWith(".avito.ru"));
        } catch (Exception ignored) { return false; }
    }

    private void load(String url) {
        if (!isAvito(url)) { toast("Разрешены только HTTPS-ссылки Авито"); return; }
        switchTo(0);
        currentUrl=url;
        status.setText("Загружается…");
        web.loadUrl(url);
    }

    private void launchBrowser(String url) {
        if (!isAvito(url)) { toast("Нужна ссылка на avito.ru"); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) { toast("Браузер не найден"); }
    }

    private void createSaved() {
        ScrollView scroll=new ScrollView(this);
        pages[1].addView(scroll);
        LinearLayout body=vertical();
        body.setPadding(dp(12),dp(14),dp(12),dp(12));
        scroll.addView(body);
        body.addView(label("Сохранённые ссылки",20));
        Button add=action("+ Добавить ссылку");
        body.addView(add);
        add.setOnClickListener(v -> {
            EditText input=field("https://www.avito.ru/...",false);
            new AlertDialog.Builder(this).setTitle("Вставьте ссылку Авито").setView(input)
              .setPositiveButton("Сохранить",(dialog,which)->saveLink(input.getText().toString().trim()))
              .setNegativeButton("Отмена",null).show();
        });
        savedItems=vertical();
        body.addView(savedItems);
    }

    private Set<String> favorites() {
        return new LinkedHashSet<>(prefs.getStringSet(FAVORITES,Collections.emptySet()));
    }

    private void saveCurrent() {
        String link=web.getUrl();
        if (!isAvito(link)) link=currentUrl;
        saveLink(link);
    }

    private void saveLink(String link) {
        if (!isAvito(link)) { toast("Откройте объявление или вставьте HTTPS-ссылку Авито"); return; }
        Set<String> set=favorites();
        if (!set.add(link)) { toast("Уже сохранено"); return; }
        prefs.edit().putStringSet(FAVORITES,set).apply();
        toast("Ссылка сохранена");
        if (tab==1) showFavorites();
    }

    private void showFavorites() {
        savedItems.removeAllViews();
        Set<String> all=favorites();
        if (all.isEmpty()) {
            savedItems.addView(label("Сохранённых ссылок пока нет.",15));
            return;
        }
        for (String link:all) {
            LinearLayout card=vertical();
            card.setPadding(dp(8),dp(10),dp(8),dp(10));
            card.setBackgroundColor(Color.WHITE);
            TextView name=label(link,13);
            card.addView(name);
            LinearLayout row=horizontal();
            Button open=action("Открыть");
            row.addView(open,new LinearLayout.LayoutParams(0,dp(49),1));
            open.setOnClickListener(v->load(link));
            Button outside=action("В браузере");
            row.addView(outside,new LinearLayout.LayoutParams(0,dp(49),1));
            outside.setOnClickListener(v->launchBrowser(link));
            Button remove=action("Удалить");
            row.addView(remove,new LinearLayout.LayoutParams(0,dp(49),1));
            remove.setOnClickListener(v->{
                Set<String> remaining=favorites();
                remaining.remove(link);
                prefs.edit().putStringSet(FAVORITES,remaining).apply();
                showFavorites();
            });
            card.addView(row);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.bottomMargin=dp(8);
            savedItems.addView(card,p);
        }
    }

    private void createCalculator() {
        ScrollView scroll=new ScrollView(this);
        pages[2].addView(scroll);
        LinearLayout body=vertical();
        body.setPadding(dp(14),dp(14),dp(14),dp(14));
        scroll.addView(body);
        body.addView(label("Расчёт возможной прибыли",20));
        purchase=amount(body,"Закупочная стоимость, ₽");
        sale=amount(body,"Цена продажи, ₽");
        shipping=amount(body,"Доставка, ₽");
        percent=amount(body,"Комиссия площадки, %");
        extras=amount(body,"Другие расходы, ₽");
        Button calculate=action("Рассчитать прибыль");
        calculate.setTextColor(Color.WHITE);
        calculate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        body.addView(calculate);
        calculation=label("Введите данные для расчёта.",17);
        calculation.setPadding(dp(4),dp(20),0,0);
        body.addView(calculation);
        calculate.setOnClickListener(v->calculateProfit());
    }

    private EditText amount(LinearLayout parent,String hint) {
        EditText edit=field(hint,true);
        parent.addView(edit,new LinearLayout.LayoutParams(-1,dp(55)));
        return edit;
    }

    private BigDecimal number(EditText input) {
        String s=input.getText().toString().trim().replace(" ","").replace(",",".");
        if (s.isEmpty()) return BigDecimal.ZERO;
        BigDecimal n=new BigDecimal(s);
        if (n.signum()<0) throw new IllegalArgumentException("Отрицательное значение");
        return n;
    }

    private void calculateProfit() {
        try {
            BigDecimal cost=number(purchase);
            BigDecimal price=number(sale);
            BigDecimal delivery=number(shipping);
            BigDecimal commissionPercent=number(percent);
            BigDecimal other=number(extras);
            if (commissionPercent.compareTo(new BigDecimal("100"))>0)
                throw new IllegalArgumentException("Комиссия выше 100%");
            BigDecimal commission=price.multiply(commissionPercent)
                .divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
            BigDecimal profit=price.subtract(cost).subtract(delivery).subtract(commission).subtract(other);
            BigDecimal margin=price.signum()==0?BigDecimal.ZERO:
                profit.multiply(new BigDecimal("100")).divide(price,1,RoundingMode.HALF_UP);
            calculation.setText("Прибыль: "+profit.toPlainString()+" ₽\n"+
                "Комиссия: "+commission.toPlainString()+" ₽\n"+
                "Маржа: "+margin.toPlainString()+" %");
            calculation.setTextColor(profit.signum()<0?Color.RED:Color.rgb(21,128,61));
        } catch (Exception e) { toast("Проверьте числа: "+e.getMessage()); }
    }

    private void switchTo(int n) {
        tab=n;
        for (int i=0;i<3;i++) {
            pages[i].setVisibility(i==n?View.VISIBLE:View.GONE);
            tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                i==n?Color.rgb(219,234,254):Color.WHITE));
        }
        if (n==1) showFavorites();
    }

    private LinearLayout vertical() {
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private LinearLayout horizontal() {
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }
    private TextView label(String s,int size) {
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(INK);
        return t;
    }
    private EditText field(String placeholder,boolean numeric) {
        EditText edit=new EditText(this);
        edit.setTextSize(14); edit.setSingleLine(true); edit.setHint(placeholder);
        if (numeric) edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return edit;
    }
    private Button action(String text) {
        Button b=new Button(this);
        b.setText(text); b.setTextSize(12); b.setAllCaps(false);
        return b;
    }
    private int dp(int val) { return Math.round(val*getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() {
        if (tab!=0) switchTo(0);
        else if (web!=null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
