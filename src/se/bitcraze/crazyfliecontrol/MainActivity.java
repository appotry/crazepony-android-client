/**
 *    ||          ____  _ __                           
 * +------+      / __ )(_) /_______________ _____  ___ 
 * | 0xBC |     / __  / / __/ ___/ ___/ __ `/_  / / _ \
 * +------+    / /_/ / / /_/ /__/ /  / /_/ / / /_/  __/
 *  ||  ||    /_____/_/\__/\___/_/   \__,_/ /___/\___/
 *
 * Copyright (C) 2013 Bitcraze AB
 *
 * Crazyflie Nano Quadcopter Client
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package se.bitcraze.crazyfliecontrol;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;

import se.bitcraze.communication.BluetoothInfo;
import se.bitcraze.communication.BluetoothInterface;
import se.bitcraze.communication.BluetoothService;
import se.bitcraze.communication.BluetoothService.BlueoothBinder;
import se.bitcraze.crazyfliecontrol.SelectConnectionDialogFragment.SelectCrazyflieDialogListener;
import se.bitcraze.crazyflielib.ConnectionAdapter;
import se.bitcraze.crazyflielib.CrazyradioLink;
import se.bitcraze.crazyflielib.CrazyradioLink.ConnectionData;
import se.bitcraze.crazyflielib.Link;
import se.bitcraze.crazyflielib.crtp.CommanderPacket;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Toast;

import com.MobileAnarchy.Android.Widgets.Joystick.DualJoystickView;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;

public class MainActivity extends Activity {

    private static final String TAG = "CrazyflieControl";

    private static final int MAX_THRUST = 65535;

    private DualJoystickView mJoysticks;
    private FlightDataView mFlightDataView;

    private int mResolution = 1000;

    private SharedPreferences mPreferences;

    private int mMaxRollPitchAngle;
    private int mMaxYawAngle;
    private int mMaxThrust;
    private int mMinThrust;
    private boolean mXmode; //determines Crazyflie flight configuration (false = +, true = x)

    private String mMaxRollPitchAngleDefaultValue;
    private String mMaxYawAngleDefaultValue;
    private String mMaxThrustDefaultValue;
    private String mMinThrustDefaultValue;

    private boolean mIsOnscreenControllerDisabled;
    private boolean mDoubleBackToExitPressedOnce = false;

    private Thread mSendJoystickDataThread;

    private String[] mDatarateStrings;

    private Controls mControls;

    private SoundPool mSoundPool;
    private boolean mLoaded;
    private int mSoundConnect;
    private int mSoundDisconnect;
    
    private Menu mainActivityMenu;
    private BluetoothAdapter mBluetoothAdapter = null;
    BluetoothService mService;
    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setDefaultPreferenceValues();

        mControls = new Controls(this, mPreferences);
        mControls.setDefaultPreferenceValues(getResources());

        mJoysticks = (DualJoystickView) findViewById(R.id.joysticks);
        mJoysticks.setMovementRange(mResolution, mResolution);

        mFlightDataView = (FlightDataView) findViewById(R.id.flightdataview);

        initializeSounds();
        
        //开启btservice，一直处于运行状态
        Intent intent = new Intent(this, BluetoothService.class);
        startService(intent);

        
        Log.v(TAG,"onCreate");
        
    }

    private void initializeSounds() {
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Load sounds
        mSoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                mLoaded = true;
            }
        });
        mSoundConnect = mSoundPool.load(this, R.raw.proxima, 1);
        mSoundDisconnect = mSoundPool.load(this, R.raw.tejat, 1);
    }

    private void setDefaultPreferenceValues(){
        // Set default preference values
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Initialize preferences
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mMaxRollPitchAngleDefaultValue = getString(R.string.preferences_maxRollPitchAngle_defaultValue);
        mMaxYawAngleDefaultValue = getString(R.string.preferences_maxYawAngle_defaultValue);
        mMaxThrustDefaultValue = getString(R.string.preferences_maxThrust_defaultValue);
        mMinThrustDefaultValue = getString(R.string.preferences_minThrust_defaultValue);

        mDatarateStrings = getResources().getStringArray(R.array.radioDatarateEntries);
    }

    private void checkScreenLock() {
        boolean isScreenLock = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_SCREEN_ROTATION_LOCK_BOOL, false);
        if(isScreenLock){
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }else{
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        mainActivityMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case R.id.action_bluetooth:
	        	beginConnectBluetooth();
	    		break;
            case R.id.preferences:
                Intent intent2 = new Intent(this, PreferencesActivity.class);
                startActivity(intent2);
                break;
        }
        return true;
    }
    
	@Override
    protected void onStart() {
        super.onStart();
        // Bind to the service
        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        
        Log.v(TAG,"onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        resetInputMethod();
        setControlConfig();
        checkScreenLock();
        
        Log.v(TAG,"onResume");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.v(TAG,"onRestart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mControls.resetAxisValues();
        Log.v(TAG,"onPause");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        unbindService(mConnection);
        Log.v(TAG,"onStop");
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	
        mSoundPool.release();
        mSoundPool = null;
        
        Intent intent = new Intent(this, BluetoothService.class);
        stopService(intent);
        
        Log.v(TAG,"onDestroy");
    }

    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.mDoubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                mDoubleBackToExitPressedOnce = false;

            }
        }, 2000);
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
            	Intent intent = new Intent(this, BluetoothActivity.class);
        		startActivity(intent);
            } else {
            	Toast.makeText(this, "蓝牙没有开启", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateFlightData(){
        mFlightDataView.updateFlightData(getPitch(), getRoll(), getThrust(), getYaw());
    }
    
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // Check that the event came from a joystick since a generic motion event could be almost anything.
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && event.getAction() == MotionEvent.ACTION_MOVE) {
            mControls.dealWithMotionEvent(event);
            updateFlightData();
            return true;
        } else {
            return super.dispatchGenericMotionEvent(event);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // TODO: works for PS3 controller, but does it also work for other controllers?
        // do not call super if key event comes from a gamepad, otherwise the buttons can quit the app
        if (event.getSource() == 1281) {
            mControls.dealWithKeyEvent(event);
            // exception for OUYA controllers
            if (!Build.MODEL.toUpperCase(Locale.getDefault()).contains("OUYA")) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void setRadioChannelAndDatarate(int channel, int datarate) {
        if (channel != -1 && datarate != -1) {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putString(PreferencesActivity.KEY_PREF_RADIO_CHANNEL, String.valueOf(channel));
            editor.putString(PreferencesActivity.KEY_PREF_RADIO_DATARATE, String.valueOf(datarate));
            editor.commit();

            Toast.makeText(this,"Channel: " + channel + " Data rate: " + mDatarateStrings[datarate] + "\nSetting preferences...", Toast.LENGTH_SHORT).show();
        }
    }

    public void disableOnscreenController() {
        Toast.makeText(this, "Using external controller", Toast.LENGTH_SHORT).show();
        mJoysticks.setOnJostickMovedListener(null, null);
        this.mIsOnscreenControllerDisabled = true;
    }

    public boolean isOnscreenControllerDisabled() {
        return mIsOnscreenControllerDisabled;
    }

    private void resetInputMethod() {
        Toast.makeText(this, "Using on-screen controller", Toast.LENGTH_SHORT).show();
        this.mIsOnscreenControllerDisabled = false;
        mJoysticks.setOnJostickMovedListener(_listenerLeft, _listenerRight);
    }

    private void setControlConfig() {
        mControls.setControlConfig();
        if (mPreferences.getBoolean(PreferencesActivity.KEY_PREF_AFC_BOOL, false)) {
            this.mMaxRollPitchAngle = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_MAX_ROLLPITCH_ANGLE, mMaxRollPitchAngleDefaultValue));
            this.mMaxYawAngle = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_MAX_YAW_ANGLE, mMaxYawAngleDefaultValue));
            this.mMaxThrust = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_MAX_THRUST, mMaxThrustDefaultValue));
            this.mMinThrust = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_MIN_THRUST, mMinThrustDefaultValue));
            this.mXmode = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_XMODE, false);
        } else {
            this.mMaxRollPitchAngle = Integer.parseInt(mMaxRollPitchAngleDefaultValue);
            this.mMaxYawAngle = Integer.parseInt(mMaxYawAngleDefaultValue);
            this.mMaxThrust = Integer.parseInt(mMaxThrustDefaultValue);
            this.mMinThrust = Integer.parseInt(mMinThrustDefaultValue);
            this.mXmode = false;
        }
    }
    
    
    private void startSendCommanderTread() {
    	mSendJoystickDataThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                	
                	CommanderPacket commanderPacket = new CommanderPacket(getRoll(), getPitch(), getYaw(), (char) (getThrust()/100 * MAX_THRUST), isXmode());
                	mService.write(commanderPacket.toByteArray());
                	
                    try {
                    	Thread.sleep(50, 0);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        mSendJoystickDataThread.start();
    }


    public float getThrust() {
        float thrust = ((mControls.getMode() == 1 || mControls.getMode() == 3) ? mControls.getRightAnalog_Y() : mControls.getLeftAnalog_Y());
        if (thrust > mControls.getDeadzone()) {
            return mMinThrust + (thrust * getThrustFactor());
        }
        return 0;
    }

    public float getRoll() {
        float roll = (mControls.getMode() == 1 || mControls.getMode() == 2) ? mControls.getRightAnalog_X() : mControls.getLeftAnalog_X();
        return (roll + mControls.getRollTrim()) * getRollPitchFactor() * mControls.getDeadzone(roll);
    }

    public float getPitch() {
        float pitch = (mControls.getMode() == 1 || mControls.getMode() == 3) ? mControls.getLeftAnalog_Y() : mControls.getRightAnalog_Y();
        return (pitch + mControls.getPitchTrim()) * getRollPitchFactor() * mControls.getDeadzone(pitch);
    }

    public float getYaw() {
        float yaw = 0;
        if(mControls.useSplitAxisYaw()){
            yaw = mControls.getSplitAxisYawRight() - mControls.getSplitAxisYawLeft();
        }else{
            yaw = (mControls.getMode() == 1 || mControls.getMode() == 2) ? mControls.getLeftAnalog_X() : mControls.getRightAnalog_X();
        }
        return yaw * getYawFactor() * mControls.getDeadzone(yaw);
    }

    public float getRollPitchFactor() {
        return mMaxRollPitchAngle;
    }

    public float getYawFactor() {
        return mMaxYawAngle;
    }

    public float getThrustFactor() {
        int addThrust = 0;
        if ((mMaxThrust - mMinThrust) < 0) {
            addThrust = 0; // do not allow negative values
        } else {
            addThrust = (mMaxThrust - mMinThrust);
        }
        return addThrust;
    }

    public boolean isXmode() {
        return this.mXmode;
    }

    private JoystickMovedListener _listenerRight = new JoystickMovedListener() {

        @Override
        public void OnMoved(int pan, int tilt) {
            mControls.setRightAnalogY((float) tilt / mResolution);
            mControls.setRightAnalogX((float) pan / mResolution);

            updateFlightData();
        }

        @Override
        public void OnReleased() {
            // Log.i("Joystick-Right", "Release");
            mControls.setRightAnalogY(0);
            mControls.setRightAnalogX(0);
        }

        public void OnReturnedToCenter() {
            // Log.i("Joystick-Right", "Center");
            mControls.setRightAnalogY(0);
            mControls.setRightAnalogX(0);
        }
    };

    private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {

        @Override
        public void OnMoved(int pan, int tilt) {
            mControls.setLeftAnalogY((float) tilt / mResolution);
            mControls.setLeftAnalogX((float) pan / mResolution);

            updateFlightData();
        }

        @Override
        public void OnReleased() {
            mControls.setLeftAnalogY(0);
            mControls.setLeftAnalogX(0);
        }

        public void OnReturnedToCenter() {
            mControls.setLeftAnalogY(0);
            mControls.setLeftAnalogX(0);
        }
    };
    

    private void beginConnectBluetooth(){
    	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "本设备不支持蓝牙", Toast.LENGTH_SHORT).show();
			return;
		}

		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			return;
		}else{
			//进入蓝牙列表页面
			Intent intent = new Intent(this, BluetoothActivity.class);
    		startActivity(intent);
		}
		
    }
    
    private void updateBluetoothItem(int state){
    	if(state == BluetoothService.STATE_SCANNING || 
				state == BluetoothService.STATE_CONNECTING){
			mainActivityMenu.findItem(R.id.action_bluetooth).setIcon(  
                    R.drawable.ic_bluetooth_search);
			
		}else if(state == BluetoothService.STATE_CONNECTED){
			mainActivityMenu.findItem(R.id.action_bluetooth).setIcon(  
                     R.drawable.ic_bluetooth_connected);
		}
    }
    
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
        	BlueoothBinder binder = (BlueoothBinder) service;
            mService = binder.getService();
            updateBluetoothItem(mService.mState);
            startSendCommanderTread();
            
            //注册回调接口来接收BluetoothService的变化  
            mService.setBluetoothInterface(new BluetoothInterface() {  
                  
                @Override  
                public void bluetoothDevicesUpdate(LinkedHashSet<BluetoothInfo> bluetoothDevices) {
                	updateBluetoothItem(mService.mState);
                }
            }); 
        }
        
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

}
