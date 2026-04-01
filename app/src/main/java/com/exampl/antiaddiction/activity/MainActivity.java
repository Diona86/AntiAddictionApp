package com.exampl.antiaddiction.activity;

import static androidx.core.content.ContentProviderCompat.requireContext;
import static com.exampl.antiaddiction.utils.Utils.formatTime;
import static com.google.android.material.internal.ContextUtils.getActivity;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;

import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exampl.antiaddiction.R;
import com.exampl.antiaddiction.adapter.UsageAdapter;
import com.exampl.antiaddiction.cloudbase.CloudBaseCallback;
import com.exampl.antiaddiction.cloudbase.CloudBaseClient;
import com.exampl.antiaddiction.fragment.CalendarFragment;
import com.exampl.antiaddiction.fragment.ClockFragment;
import com.exampl.antiaddiction.fragment.ProfileFragment;
import com.exampl.antiaddiction.fragment.StatFragment;
import com.exampl.antiaddiction.fragment.TodoFragment;
import com.exampl.antiaddiction.manager.UserManager;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.exampl.antiaddiction.utils.ThemeUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.reflect.TypeToken;

import android.view.MenuItem;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private Toolbar toolbar;

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applyTheme(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 控制状态栏图标为深色
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        //检查系统权限
        Log.d("ANTI_LOG", "正在查看相关权限");
        if (!hasUsageStatsPermission()) {
            startActivities(new Intent[]{new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)});
        } else {
            Log.d("ANTI_LOG", "已授权应用使用查看权限");
        }

        //绑定控件
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        com.google.android.material.appbar.AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);

        // 设置 Toolbar 为 ActionBar
        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        //设置系统栏边距
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // A. 顶部：让 AppBarLayout 增加 PaddingTop 来避开状态栏，但背景色会延伸上去
            //appBarLayout.setPadding(0, 0, 0, 0);

            // B. 底部：让 BottomNavigationView 增加 PaddingBottom 避开手势条
            // 同时增加高度，防止图标文字重合
            //bottomNav.setPadding(0, 0, 0, systemBars.bottom/2);

            // 根布局本身不需要 Padding，否则会产生白边
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mainFragmentContainer, new StatFragment())
                    .commit();
            toolbar.setTitle("统计"); // 初始标题
        }

        // Drawer 菜单点击
        navView.setNavigationItemSelectedListener(menuItem -> {
            int id = menuItem.getItemId();

            if (id == R.id.board_default) {
                switchBoard("default");
            } else if (id == R.id.board_study) {
                switchBoard("study");
            } else if (id == R.id.board_work) {
                switchBoard("work");
            } else if (id == R.id.board_personal) {
                switchBoard("personal");
            } else if (id == R.id.nav_add_board) {
                Toast.makeText(MainActivity.this, "待实现：添加新清单", Toast.LENGTH_SHORT).show();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // BottomNavigation 切换 Fragment + 动态 Toolbar 标题
        setupNavigation(bottomNav,navView);
    }

    private void switchBoard(String boardKey) {
        Toast.makeText(this, "切换到看板：" + boardKey, Toast.LENGTH_SHORT).show();

        TodoFragment fragment = new TodoFragment();
        Bundle bundle = new Bundle();
        bundle.putString("board_key", boardKey);
        fragment.setArguments(bundle);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.mainFragmentContainer, fragment)
                .commit();
    }
    private void setupNavigation(BottomNavigationView nav, NavigationView navView) {
        nav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String title = "";
            int id = item.getItemId();
            if (id == R.id.nav_stat) { selectedFragment = new StatFragment(); title = "统计"; }
            else if (id == R.id.nav_todo) { selectedFragment = new TodoFragment(); title = "任务"; }
            else if (id == R.id.nav_clock) { selectedFragment = new ClockFragment(); title = "专注"; }
            else if (id == R.id.nav_profile) { selectedFragment = new ProfileFragment(); title = "我的"; }
            else if (id == R.id.nav_calendar) {selectedFragment=new CalendarFragment();title="日历";};
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.mainFragmentContainer, selectedFragment).commit();
                toolbar.setTitle(title);
            }
            return true;
        });
    }
    private void checkAndEnforceLimit(long currentLocalMillis) {
        String userId = UserManager.getInstance(this).getUserId();
        String path = "/v1/rdb/rest/control_policy?userId=eq." + userId;

        TypeToken<List<Map<String, Object>>> typeToken = new TypeToken<List<Map<String, Object>>>() {};
        CloudBaseClient cloudbase = new CloudBaseClient(getString(R.string.CLOUDBASE_ENV_ID), getString(R.string.CLOUDBASE_ACCESS_TOKEN));

        cloudbase.request("GET", path, null, null, typeToken, new CloudBaseCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (data != null && !data.isEmpty()) {
                    // 1. 拿到限额（分钟）
                    int limitMinutes = ((Double) data.get(0).get("totalLimit")).intValue();

                    // 2. 本地计算当前已玩分钟
                    long currentMinutes = currentLocalMillis / 1000 / 60;

                    // 3. 核心对比
                    if (currentMinutes >= limitMinutes) {
                        showLockScreen(limitMinutes); // ！！！执行拦截！！！
                    }
                }
            }
            @Override
            public void onError(int code, String message) {}
        });
    }

    private void showLockScreen(int limit) {
        // 弹出一个全屏、不可取消的 Dialog 或跳转到一个专门的 LockActivity
        new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ 您的时间已用完")
                .setMessage("监管者设置的每日限额为 " + limit + " 分钟。请放下手机，去看看窗外的风景吧。")
                .setCancelable(false) // 强制不可取消
                .setPositiveButton("我知道了", (d, w) -> {
                    // 这里可以执行更狠的操作，比如返回桌面
                    this.finish();
                })
                .show();
    }
}