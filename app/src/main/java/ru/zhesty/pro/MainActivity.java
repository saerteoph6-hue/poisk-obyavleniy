package ru.zhesty.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private Switch gestureSwitch;
    private TextView serviceStatus;
    private boolean updatingSwitch;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245,247,253));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(30),dp(20),dp(28));
        scroll.addView(root);
        root.addView(label("Жесты PRO",29,0xFF14264F,true));
        TextView subtitle=label("Скриншот двумя пальцами вниз",17,0xFF4D5D7F,false);
        subtitle.setPadding(0,dp(6),0,dp(25));
        root.addView(subtitle);
        LinearLayout card=card();
        root.addView(card);
        card.addView(label("✌  Два пальца ↓",23,0xFF215DE3,true));
        TextView action=label("Действие: сохранить скриншот в Галерею",16,0xFF1B2B49,false);
        action.setPadding(0,dp(14),0,dp(14));
        card.addView(action);
        serviceStatus=label("Проверяю службу…",15,0xFF5E687C,false);
        card.addView(serviceStatus);
        Button settings=button("1. Разрешить службу доступности");
        addWithTopMargin(root,settings,20);
        settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout options=card();
        addWithTopMargin(root,options,16);
        options.addView(label("2. Включить жест по всему экрану",18,0xFF14264F,true));
        gestureSwitch=new Switch(this);
        gestureSwitch.setText("Два пальца вниз → скриншот");
        gestureSwitch.setTextSize(15);
        gestureSwitch.setPadding(0,dp(15),0,dp(8));
        options.addView(gestureSwitch);
        options.addView(label(
            "ВАЖНО: Android включает «Изучение касанием» для глобальных жестов. "+
            "Обычное нажатие станет выбором элемента, а для открытия потребуется двойное касание. "+
            "Вернуть привычное управление можно здесь или в настройках Android.",
            14,0xFF854616,false));
        gestureSwitch.setOnCheckedChangeListener((button,checked)->{
            if(updatingSwitch) return;
            if(checked&&!GestureScreenshotService.isConnected()){
                setSwitch(false);
                Toast.makeText(this,"Сначала включи службу доступности",Toast.LENGTH_LONG).show();
                return;
            }
            if(checked){
                setSwitch(false);
                new AlertDialog.Builder(this)
                  .setTitle("Изменится управление экраном")
                  .setMessage("Для работы жеста в любом приложении Android включает режим «Изучение касанием». "+
                   "Одно касание выбирает элемент; двойное открывает его. "+
                   "Может быть неудобно пользоваться телефоном. Включить?")
                  .setPositiveButton("Включить",(d,which)->setEnabled(true))
                  .setNegativeButton("Отмена",(d,which)->setEnabled(false))
                  .show();
            }else{
                setEnabled(false);
            }
        });
        Button tryButton=button("Проверить снимок экрана");
        addWithTopMargin(root,tryButton,16);
        tryButton.setOnClickListener(v->{
            if(!GestureScreenshotService.isConnected())
               Toast.makeText(this,"Включи службу доступности",Toast.LENGTH_SHORT).show();
            else GestureScreenshotService.testScreenshot();
        });
        TextView help=label("Как пользоваться: включи службу доступности, затем разреши "+
            "глобальный жест. Открой другое приложение и проведи двумя пальцами вниз "+
            "одновременно. Снимки находятся в Галерея → Альбомы → Screenshots. "+
            "Защищённые приложения могут блокировать скриншоты.",
            14,0xFF4D5D7F,false);
        help.setPadding(dp(4),dp(18),dp(4),0);
        root.addView(help);
        setContentView(scroll);
    }
    private void setEnabled(boolean enabled){
        getSharedPreferences(GestureScreenshotService.PREFS,MODE_PRIVATE)
          .edit().putBoolean(GestureScreenshotService.KEY_GLOBAL_MODE,enabled).apply();
        setSwitch(enabled);
        GestureScreenshotService.refreshIfRunning();
    }
    private void setSwitch(boolean enabled){
        updatingSwitch=true;
        gestureSwitch.setChecked(enabled);
        updatingSwitch=false;
    }
    @Override protected void onResume(){
        super.onResume();
        if(serviceStatus==null || gestureSwitch==null) return;
        boolean connected=GestureScreenshotService.isConnected();
        serviceStatus.setText(connected?"● Служба доступности включена":"● Служба доступности выключена");
        serviceStatus.setTextColor(connected?0xFF18875E:0xFFB05634);
        boolean enabled=getSharedPreferences(GestureScreenshotService.PREFS,MODE_PRIVATE)
           .getBoolean(GestureScreenshotService.KEY_GLOBAL_MODE,false);
        setSwitch(enabled);
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(text);v.setTextSize(size);v.setTextColor(color);
        v.setLineSpacing(dp(3),1f);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private LinearLayout card(){
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20),dp(20),dp(20),dp(20));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));
        v.setBackground(bg);v.setElevation(dp(3));
        return v;
    }
    private Button button(String text){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(text);b.setTextSize(16);b.setTextColor(Color.WHITE);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFF215DE3);bg.setCornerRadius(dp(13));
        b.setBackground(bg);b.setMinHeight(dp(56));
        b.setPadding(dp(14),dp(12),dp(14),dp(12));
        return b;
    }
    private void addWithTopMargin(LinearLayout root,View view,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(top);root.addView(view,p);
    }
    private int dp(int px){
        return Math.round(px*getResources().getDisplayMetrics().density);
    }
}
