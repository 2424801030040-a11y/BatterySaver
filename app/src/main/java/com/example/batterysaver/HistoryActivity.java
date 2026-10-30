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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;

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

        BatteryLog.initialize(getApplicationContext());
        entries.addAll(BatteryLog.snapshot());
        Collections.reverse(entries); // newest first

        ListView list = findViewById(R.id.listHistory);
        list.setEmptyView(findViewById(R.id.tvEmpty));
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, entries);
        list.setAdapter(adapter);

        registerForContextMenu(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ModeBrightness.apply(this);
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
        } else if (id == R.id.action_export) {
            exportCsv();
            return true;
        } else if (id == R.id.action_clear) {
            for (BatteryLog.Entry e : new ArrayList<>(entries)) BatteryLog.remove(e);
            entries.clear();
            adapter.notifyDataSetChanged();
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportCsv() {
        List<BatteryLog.Entry> allEntries = BatteryLog.snapshot();
        if (allEntries.isEmpty()) {
            Toast.makeText(this, R.string.export_history_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        StringBuilder csv = new StringBuilder("timestamp,battery_percent,status,temperature_c,voltage_mv,power_source\n");
        for (BatteryLog.Entry entry : allEntries) {
            csv.append(dateFormat.format(entry.time)).append(',')
                    .append(entry.level).append(',')
                    .append(csvCell(entry.status)).append(',')
                    .append(entry.temperatureTenths > 0
                            ? String.format(Locale.US, "%.1f", entry.temperatureTenths / 10f) : "")
                    .append(',').append(entry.voltageMv > 0 ? String.valueOf(entry.voltageMv) : "")
                    .append(',').append(csvCell(entry.source)).append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_history_title));
        send.putExtra(Intent.EXTRA_TEXT, csv.toString());
        startActivity(Intent.createChooser(send, getString(R.string.share_via)));
    }

    private String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
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
