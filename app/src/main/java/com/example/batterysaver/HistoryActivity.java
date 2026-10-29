package com.example.batterysaver;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private final List<BatteryLog.Entry> entries = new ArrayList<>();
    private ArrayAdapter<BatteryLog.Entry> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        entries.addAll(BatteryLog.snapshot());
        Collections.reverse(entries); // newest first

        ListView list = findViewById(R.id.listHistory);
        list.setEmptyView(findViewById(R.id.tvEmpty));
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, entries);
        list.setAdapter(adapter);

        registerForContextMenu(list);
    }

    // ---------------- Options Menu + Submenu (sort) ----------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.history_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.sort_date) {
            Collections.sort(entries, (a, b) -> Long.compare(b.time, a.time));
            adapter.notifyDataSetChanged();
        } else if (id == R.id.sort_level) {
            Collections.sort(entries, (a, b) -> Integer.compare(b.level, a.level));
            adapter.notifyDataSetChanged();
        } else if (id == R.id.action_clear) {
            for (BatteryLog.Entry e : new ArrayList<>(entries)) BatteryLog.remove(e);
            entries.clear();
            adapter.notifyDataSetChanged();
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------- Context Menu ----------------
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);
        getMenuInflater().inflate(R.menu.context_menu, menu);
        menu.setHeaderTitle(R.string.history_context_title);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null) return super.onContextItemSelected(item);

        BatteryLog.Entry entry = entries.get(info.position);
        if (item.getItemId() == R.id.ctx_delete) {
            BatteryLog.remove(entry);
            entries.remove(info.position);
            adapter.notifyDataSetChanged();
        } else if (item.getItemId() == R.id.ctx_share) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, entry.toString());
            startActivity(Intent.createChooser(send, getString(R.string.share_via)));
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
