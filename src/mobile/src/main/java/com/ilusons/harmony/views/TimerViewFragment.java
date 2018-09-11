package com.ilusons.harmony.views;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.codetroopers.betterpickers.OnDialogDismissListener;
import com.codetroopers.betterpickers.hmspicker.HmsPickerBuilder;
import com.codetroopers.betterpickers.hmspicker.HmsPickerDialogFragment;
import com.ilusons.harmony.R;
import com.ilusons.harmony.base.MusicService;
import com.ilusons.harmony.data.Music;
import com.ilusons.harmony.ref.SPrefEx;

public class TimerViewFragment extends Fragment {

	// Logger TAG
	private static final String TAG = TimerViewFragment.class.getSimpleName();

	private View root;

	private TextView text;
	private CountDownTimer countDownTimer;
	private LottieAnimationView set_timer;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		// Set view
		View v = inflater.inflate(R.layout.timer_view, container, false);

		// Set views
		root = v.findViewById(R.id.root);

		// Set timer
		text = v.findViewById(R.id.text);

		set_timer = v.findViewById(R.id.set_timer);

		set_timer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				try {
					final HmsPickerDialogFragment.HmsPickerDialogHandlerV2 handler = new HmsPickerDialogFragment.HmsPickerDialogHandlerV2() {
						@Override
						public void onDialogHmsSet(int reference, boolean isNegative, int hours, int minutes, int seconds) {
							if (isNegative) {
								long time = ((((hours * 60L) + minutes) * 60) + seconds) * 1000;
								if (time > 0L) {
									time += System.currentTimeMillis();
								}

								setTimer(getContext(), time);

								updateUI();
							} else {
								cancelTimer(getContext());

								updateUI();
							}
						}
					};
					final HmsPickerBuilder hpb = new HmsPickerBuilder()
							.setFragmentManager(getActivity().getSupportFragmentManager())
							.setStyleResId(R.style.BetterPickersDialogFragment);
					hpb.addHmsPickerDialogHandler(handler);
					hpb.setOnDismissListener(new OnDialogDismissListener() {
						@Override
						public void onDialogDismiss(DialogInterface dialoginterface) {
							hpb.removeHmsPickerDialogHandler(handler);
						}
					});
					hpb.setTimeInMilliseconds(getSleepTimerTimeLeft(getContext()));
					hpb.show();
				} catch (Exception e) {
					// Eat ?
				}

			}
		});

		updateUI();

		return v;
	}

	private void updateUI() {

		if (countDownTimer != null) {
			countDownTimer.cancel();
			countDownTimer = null;
		}

		if (getSleepTimerTimeLeft(getContext()) > 0) {

			countDownTimer = new CountDownTimer(getSleepTimerTimeLeft(getContext()), 1000) {
				@Override
				public void onTick(long time) {
					time += 1000; // HACK

					int h = (int) (time / 3600000);
					int m = (int) (time - h * 3600000) / 60000;
					int s = (int) (time - h * 3600000 - m * 60000) / 1000;
					String hh = h < 10 ? "0" + h : h + "";
					String mm = m < 10 ? "0" + m : m + "";
					String ss = s < 10 ? "0" + s : s + "";
					text.setText(hh + ":" + mm + ":" + ss);
				}

				@Override
				public void onFinish() {
					updateUI();
				}
			};
			countDownTimer.start();

			text.setText("...");

			set_timer.pauseAnimation();
			set_timer.setAnimation("clock.json", LottieAnimationView.CacheStrategy.Weak);
			set_timer.loop(true);
			set_timer.setScale(1);
			set_timer.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			set_timer.clearColorFilters();
			set_timer.addColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), android.R.color.holo_green_light), PorterDuff.Mode.MULTIPLY));
			set_timer.playAnimation();

		} else {

			text.setText("Tap above");

			set_timer.pauseAnimation();
			set_timer.setAnimation("no_notifications!.json", LottieAnimationView.CacheStrategy.Weak);
			set_timer.loop(true);
			set_timer.setScale(1);
			set_timer.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			set_timer.clearColorFilters();
			set_timer.addColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getContext(), android.R.color.holo_red_light), PorterDuff.Mode.MULTIPLY));
			set_timer.playAnimation();
		}

	}

	public static final String TAG_SPREF_ST_TIME = SPrefEx.TAG_SPREF + ".st_t";

	public static Long getSleepTimerTime(Context context) {
		try {
			return SPrefEx.get(context).getLong(TAG_SPREF_ST_TIME, 0);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0L;
	}

	public static void setSleepTimerTime(Context context, Long value) {
		try {
			SPrefEx.get(context)
					.edit()
					.putLong(TAG_SPREF_ST_TIME, value)
					.apply();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static long getSleepTimerTimeLeft(Context context) {
		long dt = getSleepTimerTime(context) - System.currentTimeMillis();

		if (dt < 0)
			dt = 0L;

		return dt;
	}

	public static class WakefulReceiver extends WakefulBroadcastReceiver {

		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "WakefulReceiver::onReceive" + System.lineSeparator() + intent);

			WakefulReceiver.completeWakefulIntent(intent);

			// Stop playback
			final Intent intentStop = new Intent(context, MusicService.class);
			intentStop.setAction(MusicService.ACTION_STOP);
			startWakefulService(context, intentStop);

			Toast.makeText(context, "Sleep timer! Stopping playback now, if active!", Toast.LENGTH_LONG).show();

			cancelTimer(context);
		}

	}

	public static class BootReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "BootReceiver::onReceive" + System.lineSeparator() + intent);

			if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {

				Long time = getSleepTimerTime(context);

				if (time > 0) {

					AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

					PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, WakefulReceiver.class), 0);

					alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, alarmIntent);

				}

			}

		}

	}

	public static void setTimer(Context context, long time) {
		setSleepTimerTime(context, time);

		if (time <= 0L)
			return;

		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		Intent intent = new Intent(context, WakefulReceiver.class);
		PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

		alarmManager.setExact(AlarmManager.RTC_WAKEUP, time, alarmIntent);

		// Enable {@code BootReceiver} to automatically restart when the
		// device is rebooted.
		ComponentName receiver = new ComponentName(context, BootReceiver.class);
		PackageManager pm = context.getPackageManager();
		pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
	}

	public static void cancelTimer(Context context) {
		setSleepTimerTime(context, 0L);

		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(context, WakefulReceiver.class);
		PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

		alarmManager.cancel(alarmIntent);

		// Disable {@code BootReceiver} so that it doesn't automatically restart when the device is rebooted.
		ComponentName receiver = new ComponentName(context, BootReceiver.class);
		PackageManager pm = context.getPackageManager();
		pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
	}

	public static void showAsDialog(Context context) {
		FragmentDialogActivity.show(context, TimerViewFragment.class, Bundle.EMPTY);
	}

	public static TimerViewFragment create() {
		TimerViewFragment f = new TimerViewFragment();
		return f;
	}

}
