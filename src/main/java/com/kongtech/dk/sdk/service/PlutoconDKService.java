package com.kongtech.dk.sdk.service;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.kongtech.dk.sdk.device.PlutoconDK;
import com.kongtech.dk.sdk.repackaged.ScanRecord;
import com.kongtech.dk.sdk.service.scanner.LollipopScanner;
import com.kongtech.dk.sdk.service.scanner.OldScanner;
import com.kongtech.dk.sdk.service.scanner.PlutoconDKScanner;
import com.kongtech.dk.sdk.utils.Plog;
import com.kongtech.dk.sdk.service.scanner.PlutoconDKScanner.ScannerCallback;


public class PlutoconDKService extends Service {

    private RequestHandler requestHandler = null;
    private Messenger messengerService = null;
    private PlutoconDKScanner plutoconDKScanner;

    private Messenger responseMessenger;

    public PlutoconDKService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        HandlerThread thread = new HandlerThread("Messenger Thread");
        thread.start();

        requestHandler = new RequestHandler(thread.getLooper());
        messengerService = new Messenger(requestHandler);

        if(Build.VERSION.SDK_INT >= 21) {
            this.plutoconDKScanner = new LollipopScanner(this.getApplicationContext(), LollipopScanner.SCAN_FOREGROUND, createScannerCallback());
        }
        else
            this.plutoconDKScanner = new OldScanner(this.getApplicationContext(), createScannerCallback());
    }

    private void sendScanResult(PlutoconDK plutoconDK){
        if(this.responseMessenger == null) return;

        Message scanResultMsg = Message.obtain(null, MessageUtil.RESPONSE_SCAN_RESULT);
        scanResultMsg.getData().putParcelable(MessageUtil.SCAN_RESULT, plutoconDK);

        try {
            this.responseMessenger.send(scanResultMsg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        requestHandler.getLooper().quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messengerService.getBinder();
    }

    private class RequestHandler extends Handler {
        private RequestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            final Messenger replyTo = msg.replyTo;
            responseMessenger = replyTo;

            switch (msg.what) {
                case MessageUtil.REQUEST_SCAN_START:
                    plutoconDKScanner.start();
                    break;
                case MessageUtil.REQUEST_SCAN_STOP:
                    plutoconDKScanner.stop();
                    break;
                case MessageUtil.REQUEST_MODE_BACKGROUND:
                    if(Build.VERSION.SDK_INT >= 21)
                        ((LollipopScanner) plutoconDKScanner).setScanMode(LollipopScanner.SCAN_BACKGROUND);
                    else
                        Plog.d("This function is only supported in later versions lollipop ");
                    break;
                case MessageUtil.REQUEST_MODE_FOREGROUND:
                    if(Build.VERSION.SDK_INT >= 21)
                        ((LollipopScanner) plutoconDKScanner).setScanMode(LollipopScanner.SCAN_FOREGROUND);
                    else
                        Plog.d("This function is only supported in later versions lollipop ");
                    break;
            }
        }
    }

    private ScannerCallback createScannerCallback() {
        return new ScannerCallback() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi, ScanRecord scanRecord) {

                PlutoconDK plutoconDK = PlutoconDK.createFromScanResult(device, scanRecord, rssi);
                if(plutoconDK != null) {
                    Plog.i("ScanResult: " + scanRecord.toString());
                    PlutoconDKService.this.sendScanResult(plutoconDK);
                }
            }
        };
    }
}
