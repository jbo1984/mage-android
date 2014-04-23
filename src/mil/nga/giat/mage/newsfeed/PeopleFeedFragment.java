package mil.nga.giat.mage.newsfeed;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.sdk.datastore.DaoStore;
import mil.nga.giat.mage.sdk.datastore.location.Location;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.j256.ormlite.android.AndroidDatabaseResults;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;

public class PeopleFeedFragment extends Fragment implements OnSharedPreferenceChangeListener {
    private PeopleCursorAdapter adapter;
    private PreparedQuery<Location> query;
    private Dao<Location, Long> lDao;
    private ViewGroup footer;
    private SharedPreferences sp;
    private long requeryTime;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> queryUpdateHandle;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_feed_people, container, false);
        setHasOptionsMenu(true);

        ListView lv = (ListView) rootView.findViewById(R.id.people_feed_list);
        footer = (ViewGroup) inflater.inflate(R.layout.feed_footer, lv, false);
        lv.addFooterView(footer, null, false);
        
        sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        sp.registerOnSharedPreferenceChangeListener(this);
        
        try {
            lDao = DaoStore.getInstance(getActivity().getApplicationContext()).getLocationDao();
            query = buildQuery(lDao, getTimeFilterId());
            Cursor c = obtainCursor(query, lDao);
            adapter = new PeopleCursorAdapter(getActivity().getApplicationContext(), c, query);
            footer = (ViewGroup) inflater.inflate(R.layout.feed_footer, lv, false);
            footer.setVisibility(View.GONE);
            lv.setAdapter(adapter);
            // adapter = new
            // NewsFeedCursorAdapter(getActivity().getApplicationContext(), c,
            // query, getActivity());
            // lv.setAdapter(adapter);
            // try {
            // ObservationHelper.getInstance(getActivity().getApplicationContext()).addListener(this);
            // } catch (ObservationException oe) {
            // oe.printStackTrace();
            // }
            // // iterator.closeQuietly();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rootView;
    }
    
    private int getTimeFilterId() {
        return sp.getInt(getResources().getString(R.string.activeTimeFilterKey), R.id.none_rb);
    }

    private Cursor obtainCursor(PreparedQuery<Location> query, Dao<Location, Long> lDao) throws SQLException {
        // build your query
        QueryBuilder<Location, Long> qb = lDao.queryBuilder();
        qb.where().gt("_id", 0);
        // this is wrong. need to figure out how to order on nested table or
        // move the correct field up
        qb.orderBy("last_modified", false);

        Cursor c = null;
        CloseableIterator<Location> iterator = lDao.iterator(query);

        // get the raw results which can be cast under Android
        AndroidDatabaseResults results = (AndroidDatabaseResults) iterator.getRawResults();
        c = results.getRawCursor();
        if (c.moveToLast()) {
            long oldestTime = c.getLong(c.getColumnIndex("last_modified"));
            Log.i("test", "last modified is: " + c.getLong(c.getColumnIndex("last_modified")));
            Log.i("test", "querying again in: " + (oldestTime - requeryTime)/60000 + " minutes");
            if (queryUpdateHandle != null) {
                queryUpdateHandle.cancel(true);
            }
            queryUpdateHandle = scheduler.schedule(new Runnable() {
                public void run() {
                    updateTimeFilter(getTimeFilterId());
                }
            }, oldestTime - requeryTime, TimeUnit.MILLISECONDS);
            c.moveToFirst();
        }
        return c;
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // TODO Add your menu entries here
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.filter, menu);
    }
    
    @Override
    public void onDestroy() {
        sp.unregisterOnSharedPreferenceChangeListener(this);
        if (queryUpdateHandle != null) {
            queryUpdateHandle.cancel(true);
        }

        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getResources().getString(R.string.activeTimeFilterKey).equalsIgnoreCase(key)) {
            updateTimeFilter(sharedPreferences.getInt(key, 0));
        }
    }

    private void updateTimeFilter(final int filterId) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    query = buildQuery(lDao, filterId);
                    adapter.changeCursor(obtainCursor(query, lDao));
                } catch (Exception e) {
                    Log.e("NewsFeedFragment", "Unable to change cursor", e);
                }
            }
        });
    }
    
    private PreparedQuery<Location> buildQuery(Dao<Location, Long> lDao, int filterId) throws SQLException {
        QueryBuilder<Location, Long> qb = lDao.queryBuilder();
        Calendar c = Calendar.getInstance();
        String title = "";
        String footerText = "";
        switch (filterId) {
        case R.id.none_rb:
            // no filter
            title += "All People";
            footerText = "All people have been returned";
            c.setTime(new Date(0));
            break;
        case R.id.last_hour_rb:
            title += "Last Hour";
            footerText = "End of results for Last Hour filter";
            c.add(Calendar.HOUR, -1);
            break;
        case R.id.last_six_hours_rb:
            title += "Last 6 Hours";
            footerText = "End of results for Last 6 Hours filter";
            c.add(Calendar.HOUR, -6);
            break;
        case R.id.last_twelve_hours_rb:
            title += "Last 12 Hours";
            footerText = "End of results for Last 12 Hours filter";
            c.add(Calendar.HOUR, -12);
            break;
        case R.id.last_24_hours_rb:
            title += "Last 24 Hours";
            footerText = "End of results for Last 24 Hours filter";
            c.add(Calendar.HOUR, -24);
            break;
        case R.id.since_midnight_rb:
            title += "Since Midnight";
            footerText = "End of results for Today filter";
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            break;
        default:
            // just set no filter
            title += "All People";
            footerText = "All people have been returned";
            c.setTime(new Date(0));
            break;
        }
        
        requeryTime = c.getTimeInMillis();
        TextView footerTextView = (TextView)footer.findViewById(R.id.footer_text);
        footerTextView.setText(footerText);
        getActivity().getActionBar().setTitle(title);
        qb.where().gt("last_modified", c.getTime()).and().eq("current_user", Boolean.FALSE).query(); 

        qb.orderBy("last_modified", false);

        return qb.prepare();
    }

    // @Override
    // public void onObservationCreated(final Collection<Observation>
    // observations) {
    // getActivity().runOnUiThread(new Runnable() {
    // @Override
    // public void run() {
    // try {
    // adapter.changeCursor(obtainCursor(query, oDao));
    // } catch (Exception e) {
    // Log.e("NewsFeedFragment", "Unable to change cursor", e);
    // }
    // }
    // });
    //
    // }
    //
    // @Override
    // public void onObservationDeleted(final Observation observation) {
    // getActivity().runOnUiThread(new Runnable() {
    // @Override
    // public void run() {
    // try {
    // adapter.changeCursor(obtainCursor(query, oDao));
    // } catch (Exception e) {
    // Log.e("NewsFeedFragment", "Unable to change cursor", e);
    // }
    // }
    // });
    // }
    //
    // @Override
    // public void onObservationUpdated(final Observation observation) {
    // getActivity().runOnUiThread(new Runnable() {
    // @Override
    // public void run() {
    // try {
    // adapter.changeCursor(obtainCursor(query, oDao));
    // } catch (Exception e) {
    // Log.e("NewsFeedFragment", "Unable to change cursor", e);
    // }
    // }
    // });
    //
    // }
}
