package com.newshub.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.newshub.app.R;

/** Single activity: bottom navigation (Лента, Категории, Избранное, Источники, Настройки). */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private View divider;
    private View root;
    private boolean bottomVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        bottomNav = findViewById(R.id.bottom_nav);
        divider = findViewById(R.id.nav_divider);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) throw new IllegalStateException("NavHostFragment missing");
        NavController nav = host.getNavController();
        NavigationUI.setupWithNavController(bottomNav, nav);
        bottomNav.setOnItemReselectedListener(item -> nav.popBackStack(item.getItemId(), false));

        nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean fullScreen = id == R.id.articleFragment || id == R.id.searchFragment;
            bottomVisible = !fullScreen;
            bottomNav.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
            divider.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
            ViewCompat.requestApplyInsets(root);
        });

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = bottomVisible ? 0 : Math.max(bars.bottom, ime.bottom);
            v.setPadding(bars.left, bars.top, bars.right, bottom);
            return insets;
        });
    }

    /** Switches the bottom navigation tab (used by "open sources" actions). */
    public void selectTab(int menuId) {
        bottomNav.setSelectedItemId(menuId);
    }
}
