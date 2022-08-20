package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.ListView;

import org.checkerframework.checker.units.qual.m;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private  static  String TAG = "MainActivity";
    JavaCameraView myCam;
    Mat mRGBA,mRGBAT,dst;
    private final int PERMISSIONS_READ_CAMERA=1;
    private static List<Object[]> currentLabels = new ArrayList<Object[]>();
    private List<String> directions = new ArrayList<>();


    BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case BaseLoaderCallback.SUCCESS: {
                    myCam.enableView();
                    break;
                }
                default:   {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    static
    {
        if(OpenCVLoader.initDebug()){ Log.d(TAG,"Opencv installed");}
        else{ Log.d(TAG,"Opencv bad");}
    }

    private ObjectDetectorClass objectDetectorClass;

    private TextView txvResult;
    private  TextView txvResult2;
    private int RqSpeechRec=102;

   private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status


    private  BluetoothAdapter bta;
    private BluetoothSocket btSocket = null;

    public List<String> labelList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"onCreate");
        setContentView(R.layout.activity_main);
        directions.add("right");
        directions.add("left");
        directions.add("forward");
        directions.add("backward");
        txvResult = (TextView) findViewById(R.id.txvResult);
        txvResult2 = (TextView) findViewById(R.id.txvResult2);

        myCam = (JavaCameraView) findViewById(R.id.myCamera);
        myCam.setVisibility(SurfaceView.VISIBLE);
        myCam.setCvCameraViewListener(this);

          bta =  BluetoothAdapter.getDefaultAdapter();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSIONS_READ_CAMERA);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            Log.d(TAG, "PERMISSIOns granted");
            myCam.setCameraPermissionGranted();
            // Permission has already been granted
        }
        try{
            objectDetectorClass = new ObjectDetectorClass(getAssets(),"ssd_mobilenet_v1_1_metadata_1.tflite", "labelmap.txt",300);
            labelList=objectDetectorClass.labelList;
            Log.d(TAG,"Model success");
        }catch (IOException e){
            Log.d(TAG,"Model failed");
            e.printStackTrace();
        }
         mRGBA = new Mat();
         mRGBAT = new Mat();
    }

    public void getSpeechInput(View view) {

        if(!SpeechRecognizer.isRecognitionAvailable(this))
            Toast.makeText(this,"Speech Recognition Not Available",Toast.LENGTH_SHORT).show();
        else {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            startActivityForResult(intent, RqSpeechRec);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RqSpeechRec && resultCode == Activity.RESULT_OK) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            txvResult.setText(result.get(0));
            OutputStream outputStream = null;
            Object[] ree = stringContainsItemFromList((result.get(0)),labelList);
            if((boolean)ree[0] == true && btSocket == null){//!=
                Object[] hydra = stringContainsItemFromHybrid((String)ree[1] , currentLabels);
                if((boolean)hydra[0] == true){
                    int left = (Integer) hydra[2];//Placement of the phone Min=50 , Max=950 (In x coordinates)
                    new Thread(()->{directRobot(left,(String) hydra[1]);}).start();
                }

            }
            else{
            Object[] re = stringContainsItemFromList((result.get(0)),directions);
                if((boolean)re[0] == true && btSocket != null){
                  sendChar(((String) re[1]).toUpperCase().charAt(0));//Sends The first Letter capitallized (Lazy rather than mapping)
            }
            }
        }
    }
    
    //Keep sending updated coordinates as long as the robot has not send acknowledgment of arrival to goal
    public void directRobot(int left,String label){
        try {
            while(true){
              InputStream inputStream = btSocket.getInputStream();
              if(inputStream.available() > 0){
                char C=(char)inputStream.read();
                if(C == 'S') {
                    sendChar('S');
                    return;
                }
              }
                // The goal here is to center the phone relative to the detected object
                if(left > 650){Log.d(TAG,"Go Right"); // if x coordinate is greater
                runOnUiThread(new Runnable() {public void run() {// than 650 then go right to decrease it
                    txvResult2.setText("Right");sendChar('R');}});
                }
                else if(left < 450){Log.d(TAG,"Go Left");// if x coordinate is less
                runOnUiThread(new Runnable() {public void run() {// than 450 then go left to increase it
                    txvResult2.setText("Left");sendChar('L');
                }});}
                else{Log.d(TAG,"Go Forward");//if non of the above go forward
                runOnUiThread(new Runnable() {public void run()// (meaning the object is in the center)
                              {txvResult2.setText("Forward");sendChar('F');}}
                );}
                Thread.sleep(100);
                left = findNeededLeft(currentLabels,label);
            }
        }
        /*IOException|InterruptedException e*/
        catch (Exception e){e.printStackTrace();}
        //Keep sending updated coordinates as long as the robot has not send acknowledgment of arrival to goal
        return;
    }
    public int findNeededLeft(List<Object[]> l,String needed){
              for(Object[] ob : l){
                  if(((String)ob[0]).equals(needed)) return (Integer)ob[1];
              }
              return 0;//when not found in new frame go left
    }
    public boolean sendChar(char s){
        OutputStream outputStream = null;
        try {
            outputStream = btSocket.getOutputStream();//sending instructions
            outputStream.write(s);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    public static Object[] stringContainsItemFromList(String inputStr, List<String> items)
    {
        String matched = new String();
        for(int i =0; i < items.size(); i++)
        {
            if(inputStr.contains(items.get(i))) {
                matched = items.get(i);
                Object[] Out = new Object[2];
                Out[0] = true;
                Out[1] = matched;
                return Out;
            }
        }
        Object[] Out = new Object[2];
        Out[0] = false;
        Out[1] = "";
        return Out;


    }
    public static Object[] stringContainsItemFromHybrid(String inputStr, List<Object[]> items)
    {
        List<Object[]> results = new ArrayList<Object[]>();
        for(int i =0; i < items.size(); i++)
        {
            String a = (String)(items.get(i))[0];
            Integer b = (Integer)(items.get(i))[1];
            if(inputStr.contains(a)) {
                Object[] Out = new Object[3];
                Out[0] = true;
                Out[1] = a;
                Out[2] = b;
                results.add(Out);
            }
        }
        Object[] Out = new Object[3];
        if(results.size() == 0) {
            Out[0] = false;
            Out[1] = "";
            Out[2] = 0;
        }
        else{
            int best = Math.abs(((Integer) (results.get(0))[2]) - 550);
            Object[] bestOb = results.get(0);
            for(Object[] ob : results){
                if(Math.abs(((Integer)ob[2]) - 550) < best){ best = Math.abs(((Integer)ob[2]) - 550); bestOb = ob;}
            }
            Out[0] = true;
            Out[1] = bestOb[1];
            Out[2] = bestOb[2];
        }
        return Out;
    }

    public void doBlue(View view) {
        if (!bta.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        System.out.println(bta.getBondedDevices());
        BluetoothDevice HC = null;
        for(BluetoothDevice bluedevice : bta.getBondedDevices()){
            String name = bluedevice.getName();
            if(bluedevice.getName().equals("EBRAHEM_YAZAN")){HC = bluedevice;break;}
        }
        if(HC == null) {System.out.println("NO HC FOUND"); return;}
        int attempts = 0;
        do{
        try {
            btSocket = HC.createInsecureRfcommSocketToServiceRecord(BTMODULEUUID);
            btSocket.connect();
            String status = (btSocket.isConnected()) ? "!!!!!!!!!!!!!!!!!!**TRUE**!!!!!!!!!!" : "False";
            System.out.println(status);
        }catch (IOException e) {e.printStackTrace();}
          attempts++;
         }while (!btSocket.isConnected() && attempts < 5);

        try {
            OutputStream outputStream = btSocket.getOutputStream();
            outputStream.write(48);
        }catch (IOException e) {e.printStackTrace();}

        try {
            InputStream inputStream = btSocket.getInputStream();
          //  inputStream.skip(inputStream.available());//cleaning buffer
            if(inputStream.available() > 0){
            for(int i=0;i<26;i++){
                byte b = (byte) inputStream.read();
                System.out.println((char) b);
            }
            }
        }catch (IOException e){e.printStackTrace();}

        try{
            btSocket.close();
        }catch (IOException e){e.printStackTrace();}
    }
/*
    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG,"onCameraViewStareted");
        mRGBA = new Mat(height,width, CvType.CV_8UC4);

    }
*/
    @Override
    public void onCameraViewStopped() {
        Log.d(TAG,"onCameraViewStopped");
        mRGBA.release();
    }

/*
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "onCameraFrame");
        if(mRGBAT != null){
            mRGBAT.release();
        }
        mRGBA = inputFrame.rgba();
        mRGBAT = mRGBA.t();
        Core.flip(mRGBA.t(), mRGBAT, 1);
        Imgproc.resize(mRGBAT, mRGBAT, mRGBA.size());
        return mRGBAT;
    }
*/
@Override

public void onCameraViewStarted(int width, int height)
{
    Log.d(TAG,"onCameraViewStarted");
    mRGBAT = new Mat();
    dst = new Mat();
}
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
    {
        if(dst != null){dst.release();dst=new Mat();}
        mRGBA = inputFrame.rgba();
        Core.transpose(mRGBA, mRGBAT);
        Core.flip(mRGBAT, mRGBAT, 1);
        Imgproc.resize(mRGBAT, dst, mRGBA.size());
        dst = objectDetectorClass.recognizeImage(dst);
        mRGBA.release();
        mRGBA = new Mat();
        mRGBAT.release();
        mRGBAT = new Mat();
        return dst;
    }
   /* public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
    {
        //Log.d(TAG,"onCameraFrame");
        Mat mRGBA = inputFrame.rgba();
        Mat mRGBAT = new Mat();
        Mat dst = new Mat();

        Core.transpose(mRGBA, mRGBAT);
        Core.flip(mRGBAT, mRGBAT, 1);
        Imgproc.resize(mRGBAT, dst, mRGBA.size());
        dst = objectDetectorClass.recognizeImage(dst);

        return dst;
    }*/

    public static void sendCurrentCameraObjects(List<Object[]> labels){
        currentLabels = labels;
    }

/*public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
{
    mRGBA=inputFrame.rgba();


    // now call that function
    Mat out=new Mat();
    out=objectDetectorClass.recognizeImage(mRGBA);

    return out;
}*/

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy");
        if(myCam != null){
            myCam.disableView();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG,"onPause");
        if(myCam != null){
            myCam.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"onResume");
        if(OpenCVLoader.initDebug()){
            Log.d(TAG,"Opencv installed again");
        baseLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
        }
        else{
            Log.d(TAG,"Opencv bad ");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION,this,baseLoaderCallback);
        }

    }

    
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        // Ensure that this result is for the camera permission request
        if (requestCode == PERMISSIONS_READ_CAMERA) {
            // Check if the request was granted or denied
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // The request was granted -> tell the camera view
                myCam.setCameraPermissionGranted();
            } else {
                // The request was denied -> tell the user and exit the application
                Toast.makeText(this, "Camera permission required.",
                        Toast.LENGTH_LONG).show();
                this.finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}

