package com.lantouzi.wheelview.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.lantouzi.wheelview.R;
import com.lantouzi.wheelview.WheelView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

	private WheelView mWheelView, mWheelView2;
	private TextView mSelectedTv, mChangedTv;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mWheelView = findViewById(R.id.wheelview);
		mWheelView2 = findViewById(R.id.wheelview2);
		mSelectedTv = findViewById(R.id.selected_tv);
		mChangedTv = findViewById(R.id.changed_tv);

		final List<String> items = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			items.add(String.valueOf(i));
		}

		mWheelView.setItems(items);
		mWheelView2.setItems(items);
		mWheelView.selectIndex(8);

		mSelectedTv.setText(String.format("onWheelItemSelected：%1$s", ""));
		mChangedTv.setText(String.format("onWheelItemChanged：%1$s", ""));

		mWheelView.setOnWheelItemSelectedListener(new WheelView.OnWheelItemSelectedListener() {
			@Override
			public void onWheelItemSelected(WheelView wheelView, int position) {
				mSelectedTv.setText(String.format("onWheelItemSelected：%1$s", wheelView.getItems().get(position)));
			}

			@Override
			public void onWheelItemChanged(WheelView wheelView, int position) {
				mChangedTv.setText(String.format("onWheelItemChanged：%1$s", wheelView.getItems().get(position)));
			}
		});

	}
}
