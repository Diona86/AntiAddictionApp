package com.exampl.antiaddiction.activity;

import static com.exampl.antiaddiction.utils.Utils.formatTime;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import androidx.fragment.app.Fragment;
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
import android.widget.ProgressBar;
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
import com.exampl.antiaddiction.fragment.ClockFragment;
import com.exampl.antiaddiction.fragment.ProfileFragment;
import com.exampl.antiaddiction.fragment.StatFragment;
import com.exampl.antiaddiction.fragment.TodoFragment;
import com.exampl.antiaddiction.model.AppUsageInfo;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.FirebaseApp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private boolean hasUsageStatsPermission(){
        AppOpsManager appOps=(AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode =appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),getPackageName());
        return mode==AppOpsManager.MODE_ALLOWED;
    }
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Log.d("ANTI_LOG","正在查看相关权限");

        if(!hasUsageStatsPermission())
            startActivities(new Intent[]{new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)});
        else
            Log.d("ANTI_LOG","已授权应用使用查看权限");
        BottomNavigationView nav=findViewById(R.id.bottomNav);
        if(savedInstanceState==null){
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mainFragmentContainer,new StatFragment())
                    .commit();
        }
        nav.setOnItemSelectedListener(item->{
            Fragment selectedFragment =null;
            int id =item.getItemId();
            if (id == R.id.nav_stat) {
                selectedFragment = new StatFragment();
            } else if (id == R.id.nav_todo) {
                selectedFragment = new TodoFragment();
            } else if (id == R.id.nav_clock) {
                selectedFragment = new ClockFragment();
            } else if (id == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }
            if(selectedFragment!=null){
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.mainFragmentContainer,selectedFragment)
                        .commit();
            }
            return  true;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}