package com.quanglewangle.peter.rocloc;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.quanglewangle.peter.rocloc.data.Site;

public class MainActivity extends AppCompatActivity {

    private SearchFragment searchFragment;
    private SitesFragment  sitesFragment;
    private PinsFragment   pinsFragment;
    private MapFragment    mapFragment;

    private int currentNavId = R.id.nav_search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        searchFragment = new SearchFragment();
        sitesFragment  = new SitesFragment();
        pinsFragment   = new PinsFragment();
        mapFragment    = new MapFragment();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, searchFragment, "search")
                .add(R.id.fragmentContainer, sitesFragment,  "sites")
                .add(R.id.fragmentContainer, pinsFragment,   "pins")
                .add(R.id.fragmentContainer, mapFragment,    "map")
                .hide(sitesFragment)
                .hide(pinsFragment)
                .hide(mapFragment)
                .commit();

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentNavId) return true;
            showFragment(id);
            return true;
        });
    }

    private void showFragment(int navId) {
        Fragment show = fragmentFor(navId);
        Fragment hide = fragmentFor(currentNavId);
        if (show == null || hide == null) return;
        currentNavId = navId;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.hide(hide).show(show).commit();
        updateTitle(navId);
    }

    private Fragment fragmentFor(int navId) {
        if (navId == R.id.nav_search) return searchFragment;
        if (navId == R.id.nav_sites)  return sitesFragment;
        if (navId == R.id.nav_pins)   return pinsFragment;
        if (navId == R.id.nav_map)    return mapFragment;
        return null;
    }

    private void updateTitle(int navId) {
        if (getSupportActionBar() == null) return;
        if (navId == R.id.nav_search) getSupportActionBar().setTitle(R.string.app_name);
        else if (navId == R.id.nav_sites) getSupportActionBar().setTitle(R.string.nav_sites);
        else if (navId == R.id.nav_pins)  getSupportActionBar().setTitle(R.string.nav_pins);
        else if (navId == R.id.nav_map)   getSupportActionBar().setTitle(R.string.nav_map);
    }

    public void showSiteOnMap(Site site) {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_map);
        showFragment(R.id.nav_map);
        mapFragment.goToSite(site);
    }
}
