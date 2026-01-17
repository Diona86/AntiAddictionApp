package com.exampl.antiaddiction.activity;

import static com.exampl.antiaddiction.utils.Utils.formatTime;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.adapter.UsageAdapter;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.google.firebase.FirebaseApp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private boolean hasUsageStatsPermission(){
        AppOpsManager appOps=(AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode =appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),getPackageName());
        return mode==AppOpsManager.MODE_ALLOWED;
    }
    public List<AppUsageInfo> getUsageStats() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = getPackageManager(); // 获取管家实例

        List<AppUsageInfo> list = new ArrayList<>();
        long endTime = System.currentTimeMillis();
        long startTime = endTime - 1000 * 60 * 60 * 24; // 最近 24 小时

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (stats != null) {
            for (UsageStats usageStats : stats) {
                long totalTime = usageStats.getTotalTimeInForeground();
                if (totalTime > 0) {
                    String packageName = usageStats.getPackageName();

                    String appName = "";
                    Drawable appIcon = null;

                    try {
                        // --- 核心点：通过包名拿应用信息 ---
                        ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                        appName = pm.getApplicationLabel(ai).toString(); // 拿到应用名（如：微信）
                        appIcon = pm.getApplicationIcon(ai);            // 拿到图标 Drawable
                    } catch (PackageManager.NameNotFoundException e) {
                        // 如果应用刚被卸载，可能找不到，给个默认值
                        appName = packageName;
                        appIcon = ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
                    }

                    // 转换时间格式（把毫秒转成 01h 20m 这种）
                    String timeStr = formatTime(totalTime);

                    // 添加到你刚才写的 List 里
                    list.add(new AppUsageInfo(appName, packageName, appIcon, totalTime, timeStr));

                    Log.d("ANTI_LOG", "应用：" + appName + " 时长：" + timeStr);
                }
            }
        }
        return list;
        // --- 最后把数据交给 RecyclerView ---
        // 假设你已经在 onCreate 里初始化了 adapter
        // adapter.updateData(list);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);
        Log.d("ANTI_LOG","正在查看相关权限");
        if(!hasUsageStatsPermission())
            startActivities(new Intent[]{new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)});
        else
            Log.d("ANTI_LOG","已授权应用使用查看权限");
        RecyclerView rvUsage = findViewById(R.id.rvUsage);
        rvUsage.setLayoutManager(new LinearLayoutManager(this));
        UsageAdapter adapter=new UsageAdapter(getUsageStats());
        rvUsage.setAdapter(adapter);
        TextView txtTotalTime = findViewById(R.id.txtTotalTime);
        txtTotalTime.setText(txtTotalTime.getText());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}