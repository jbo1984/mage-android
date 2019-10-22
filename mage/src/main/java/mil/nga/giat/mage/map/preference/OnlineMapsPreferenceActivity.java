package mil.nga.giat.mage.map.preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.map.cache.CacheProvider;
import mil.nga.giat.mage.sdk.datastore.layer.Layer;
import mil.nga.giat.mage.sdk.datastore.layer.LayerHelper;
import mil.nga.giat.mage.sdk.datastore.user.EventHelper;
import mil.nga.giat.mage.sdk.event.ILayerEventListener;
import mil.nga.giat.mage.sdk.fetch.ImageryServerFetch;


/**
 * This activity is the view component for online maps
 *
 */
public class OnlineMapsPreferenceActivity extends AppCompatActivity {

    /**
     * logger
     */
    private static final String LOG_NAME = OnlineMapsPreferenceActivity.class.getName();

    /**
     * Fragment showing the actual online map URLs
     */
    private OnlineMapsListFragment onlineMapsFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_maps);

        onlineMapsFragment = (OnlineMapsListFragment) getSupportFragmentManager().findFragmentById(R.id.online_maps_fragment);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putStringArrayListExtra(MapPreferencesActivity.ONLINE_MAPS_DATA_KEY, onlineMapsFragment.getSelectedOverlays());
        setResult(Activity.RESULT_OK, intent);

        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class OnlineMapsListFragment extends ListFragment  implements ILayerEventListener {

        private OnlineMapsAdapter onlineMapsAdapter;
        private MenuItem refreshButton;
        private View contentView;
        private View noContentView;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setHasOptionsMenu(true);
        }


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_online_maps, container, false);

            contentView = view.findViewById(R.id.online_maps_content);
            noContentView = view.findViewById(R.id.online_maps_no_content);

            //TODO request and/or check for any needed permissions

            return view;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ListView listView = getListView();
            listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        }

        @Override
        public void onResume() {
            super.onResume();
            getListView().setEnabled(false);
        }

        @Override
        public void onPause() {
            super.onPause();
            LayerHelper.getInstance(getActivity()).removeListener(this);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.online_maps_menu, menu);

            refreshButton = menu.findItem(R.id.online_maps_refresh);
            refreshButton.setEnabled(false);

            onLayerCreated(null);

            LayerHelper.getInstance(getActivity()).addListener(this);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.online_maps_refresh:
                    manualRefresh();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        /**
         * This is called when the user click the refresh button
         *
         */
        @UiThread
        private void manualRefresh() {
            refreshButton.setEnabled(false);
            getListView().setEnabled(false);
            noContentView.setVisibility(View.VISIBLE);
            contentView.setVisibility(View.GONE);
            ((TextView) noContentView.findViewById(R.id.online_maps_title)).setText(getResources().getString(R.string.online_maps_no_content_loading));
            noContentView.findViewById(R.id.online_maps_summary).setVisibility(View.GONE);
            noContentView.findViewById(R.id.online_maps_progressBar).setVisibility(View.VISIBLE);

            onlineMapsAdapter.clear();
            onlineMapsAdapter.notifyDataSetChanged();

            final Context c = getActivity().getApplicationContext();
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    ImageryServerFetch imageryServerFetch = new ImageryServerFetch(c);
                    try {
                        imageryServerFetch.fetch();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            new Thread(runnable).start();
        }

        @Override
        public void onLayerCreated(Layer layer) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Collection<Layer> layers = new ArrayList<>();

                    try {
                        layers = LayerHelper.getInstance(getActivity()).readByEvent(EventHelper.getInstance(getActivity()).getCurrentEvent(), "Imagery");
                    } catch(Exception e) {
                        Log.e(LOG_NAME, "Problem getting layers.", e);
                    }

                    ListView listView = getListView();
                    listView.clearChoices();

                    //TODO what to do with XYZ imagery layers??

                    onlineMapsAdapter = new OnlineMapsAdapter(getActivity(), new ArrayList<>(layers));
                    setListAdapter(onlineMapsAdapter);

                    // Set what should be checked based on preferences.
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    Set<String> overlays = preferences.getStringSet(getResources().getString(R.string.onlineMapsKey), Collections.<String> emptySet());
                    for (int i = 0; i < listView.getCount(); i++) {
                        Layer layer = (Layer) listView.getItemAtPosition(i);
                        if (overlays.contains(layer.getId().toString())) {
                            listView.setItemChecked(i, true);
                        }
                    }

                    if (!layers.isEmpty()) {
                        noContentView.setVisibility(View.GONE);
                        contentView.setVisibility(View.VISIBLE);
                    } else {
                        noContentView.setVisibility(View.VISIBLE);
                        contentView.setVisibility(View.GONE);
                        ((TextView) noContentView.findViewById(R.id.online_maps_title)).setText(getResources().getString(R.string.online_maps_no_content_text));
                        noContentView.findViewById(R.id.online_maps_summary).setVisibility(View.VISIBLE);
                        noContentView.findViewById(R.id.online_maps_progressBar).setVisibility(View.GONE);
                    }

                    refreshButton.setEnabled(true);
                    getListView().setEnabled(true);
                }
            });
        }

        @Override
        public void onError(Throwable error) {

        }

        public ArrayList<String> getSelectedOverlays() {
            //TODO implement, since this does not work ATM with the toggle switch
            ArrayList<String> overlays = new ArrayList<>();
            SparseBooleanArray checked = getListView().getCheckedItemPositions();
            for (int i = 0; i < checked.size(); i++) {
                if (checked.valueAt(i)) {
                    Layer layer = (Layer) getListView().getItemAtPosition(checked.keyAt(i));
                    overlays.add(layer.getId().toString());
                }
            }

            return overlays;
        }

        @Override
        public void onLayerUpdated(Layer layer) {

        }
    }

    /**
     *
     * <p></p>
     * <b>ALL public methods MUST be made on the UI thread to ensure concurrency.</b>
     */
    @UiThread
    public static class OnlineMapsAdapter extends ArrayAdapter<Layer> {
        private final List<Layer> layers;

        public OnlineMapsAdapter(Context context, List<Layer> overlays) {
            super(context, R.layout.online_maps_list_item, R.id.online_maps_title, overlays);

            this.layers = overlays;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            Layer layer = getItem(position);
            final String name = layer.getName();
            TextView title = view.findViewById(R.id.online_maps_title);
            title.setText(name);

            TextView summary = view.findViewById(R.id.online_maps_summary);
            summary.setText(layer.getUrl());

            View progressBar = view.findViewById(R.id.online_maps_progressBar);
            progressBar.setVisibility(layer.isLoaded() ? View.GONE : View.VISIBLE);

            View sw = view.findViewById(R.id.online_maps_toolbar_switch);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isChecked = ((Checkable)v).isChecked();
                    if(isChecked) {
                        CacheProvider.getInstance(getContext()).enableAndRefreshTileOverlays(name);
                    }else{
                        CacheProvider.getInstance(getContext()).removeCacheOverlay(name);
                    }
                }
            });


            return view;
        }

        @Override
        public int getPosition(Layer layer) {
            for (int i = 0; i < layers.size(); i++) {
                if (layer.equals(layers.get(i))) {
                    return i;
                }
            }

            return -1;
        }

        @Override
        public Layer getItem(int index) {
            Layer layer = null;

            try {
                layer = layers.get(index);
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.e(LOG_NAME, "Why out of bounds?", e);
            }

            return layer;
        }

        @Override
        public long getItemId(int index) {
            return index;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

    }
}
