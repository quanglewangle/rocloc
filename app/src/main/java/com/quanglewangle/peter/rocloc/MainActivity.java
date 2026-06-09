package com.quanglewangle.peter.rocloc;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.quanglewangle.peter.rocloc.data.Site;

public class MainActivity extends AppCompatActivity {

    private final Fragment[] frags = new Fragment[4];
    private int currentIdx = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        // Only create Search fragment eagerly; others are created on first selection.
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

    private void switchTo(int idx) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        // Hide current
        if (frags[currentIdx] != null) ft.hide(frags[currentIdx]);

        // Create lazily if needed
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
            default: return new SearchFragment();
        }
    }

    private int indexFor(int navId) {
        if (navId == R.id.nav_sites) return 1;
        if (navId == R.id.nav_pins)  return 2;
        if (navId == R.id.nav_map)   return 3;
        return 0;
    }

    private String tagFor(int idx) {
        return new String[]{"search", "sites", "pins", "map"}[idx];
    }

    private void updateTitle(int idx) {
        if (getSupportActionBar() == null) return;
        int[] titles = {R.string.app_name, R.string.nav_sites, R.string.nav_pins, R.string.nav_map};
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
