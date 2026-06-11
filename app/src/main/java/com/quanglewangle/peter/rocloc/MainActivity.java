package com.quanglewangle.peter.rocloc;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.quanglewangle.peter.rocloc.BuildConfig;
import com.quanglewangle.peter.rocloc.data.Site;

public class MainActivity extends AppCompatActivity {

    private final Fragment[] frags = new Fragment[5];
    private int currentIdx = 0;
    private SettingsFragment settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("v" + BuildConfig.VERSION_NAME + " build " + BuildConfig.VERSION_CODE);
        }

        frags[0] = new SearchFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, frags[0], "search")
                .commit();

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int idx = indexFor(item.getItemId());
            if (idx == currentIdx) return true;
            switchTo(idx);
            return true;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            showSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettings() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (frags[currentIdx] != null) ft.hide(frags[currentIdx]);
        if (settingsFragment == null) {
            settingsFragment = new SettingsFragment();
            ft.add(R.id.fragmentContainer, settingsFragment, "settings");
        } else {
            ft.show(settingsFragment);
        }
        ft.addToBackStack("settings").commit();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.nav_settings);
        getSupportFragmentManager().addOnBackStackChangedListener(new androidx.fragment.app.FragmentManager.OnBackStackChangedListener() {
            @Override public void onBackStackChanged() {
                if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                    getSupportFragmentManager().removeOnBackStackChangedListener(this);
                    updateTitle(currentIdx);
                }
            }
        });
    }

    private void switchTo(int idx) {
        // Dismiss settings if visible
        if (settingsFragment != null && !settingsFragment.isHidden()) {
            getSupportFragmentManager().popBackStack();
        }

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (frags[currentIdx] != null) ft.hide(frags[currentIdx]);

        if (frags[idx] == null) {
            frags[idx] = createFragment(idx);
            ft.add(R.id.fragmentContainer, frags[idx], tagFor(idx));
        } else {
            ft.show(frags[idx]);
        }

        ft.commit();
        currentIdx = idx;
        updateTitle(idx);
    }

    private Fragment createFragment(int idx) {
        switch (idx) {
            case 0: return new SearchFragment();
            case 1: return new SitesFragment();
            case 2: return new PinsFragment();
            case 3: return new MapFragment();
            case 4: return new HereFragment();
            default: return new SearchFragment();
        }
    }

    private int indexFor(int navId) {
        if (navId == R.id.nav_sites) return 1;
        if (navId == R.id.nav_pins)  return 2;
        if (navId == R.id.nav_map)   return 3;
        if (navId == R.id.nav_here)  return 4;
        return 0;
    }

    private String tagFor(int idx) {
        return new String[]{"search", "sites", "pins", "map", "here"}[idx];
    }

    private void updateTitle(int idx) {
        if (getSupportActionBar() == null) return;
        int[] titles = {R.string.app_name, R.string.nav_sites, R.string.nav_pins, R.string.nav_map, R.string.nav_here};
        getSupportActionBar().setTitle(titles[idx]);
    }

    public void showSiteOnMap(Site site) {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_map);
        switchTo(3);
        if (frags[3] instanceof MapFragment) {
            ((MapFragment) frags[3]).goToSite(site);
        }
    }
}
