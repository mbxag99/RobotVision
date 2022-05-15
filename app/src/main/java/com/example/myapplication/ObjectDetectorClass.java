package com.example.myapplication;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;


import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ObjectDetectorClass {
    private Interpreter interpreter;
    public List<String> labelList;
    private int INPUTS_SIZE;
    private int PIXEL_SIZE=3;
    private int IMAGE_MEAN=0;
    private float IMAGE_STD=255.0f;
    private GpuDelegate gpuDelegate;
    private int height=0;
    private int width = 0;
    public List<Object[]> currentLabels= new ArrayList<Object[]>();
    private  Map<Integer,Object> output_map;
    private Bitmap bitmapp=Bitmap.createBitmap(1056,704,Bitmap.Config.ARGB_8888);

    ObjectDetectorClass(AssetManager assetManager,String modelPath,String labelPath,int inputSize) throws IOException{

         INPUTS_SIZE= inputSize;
        Interpreter.Options options=new Interpreter.Options();
        gpuDelegate=new GpuDelegate();
        options.addDelegate(gpuDelegate);
        options.setNumThreads(5);
        output_map = new TreeMap<>();
        interpreter=new Interpreter(loadModelFile(assetManager,modelPath),options);
        labelList=loadLabelList(assetManager,labelPath);

    }

    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws  IOException{
        List<String> labelList=new ArrayList<>();
        //  new reader
        BufferedReader reader=new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        // loop through each line and store it to labelList
        while ((line=reader.readLine())!=null){
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws  IOException{
        AssetFileDescriptor fileDescriptor=assetManager.openFd(modelPath);
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset =fileDescriptor.getStartOffset();
        long declaredLength=fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);
    }
//704 * 1056
    public Mat recognizeImage(Mat matImage){
        Utils.matToBitmap(matImage,bitmapp);
        height=bitmapp.getHeight();
        width=bitmapp.getWidth();
        Object[] input=new Object[1];
        input[0]=convertBitmapToByteBuffer(Bitmap.createScaledBitmap(bitmapp,INPUTS_SIZE,INPUTS_SIZE,false));

        // add it to object_map;
        output_map.put(0,new float[1][10][4]);
        output_map.put(1,new float[1][10]);
        output_map.put(2,new float[1][10]);

        // now predict
        interpreter.runForMultipleInputsOutputs(input,output_map);

        Object value=output_map.get(0);
        Object Object_class=output_map.get(1);
        Object score=output_map.get(2);
        // loop through each object
        // as output has only 10 boxes
         currentLabels.clear();
        for (int i=0;i<10;i++){
            float class_value=(float) Array.get(Array.get(Object_class,0),i);
            float score_value=(float) Array.get(Array.get(score,0),i);
            // define threshold for score
            if(score_value>0.5){
                Object box1=Array.get(Array.get(value,0),i);
                // we are multiplying it with Original height and width of frame

                float top=(float) Array.get(box1,0)*height;
                float left=(float) Array.get(box1,1)*width;
                float bottom=(float) Array.get(box1,2)*height;
                float right=(float) Array.get(box1,3)*width;
              //  Point first = new Point((left),(height-top));
                //Point second = new Point((width-right),(bottom));
                Point middlePoint = new Point((left+right)/2,(top+bottom)/2);
                // draw rectangle in Original frame //  starting point    // ending point of box  // color of box       thickness
                Imgproc.rectangle(matImage,new Point(left,top),new Point(right,bottom),new Scalar(0, 255, 0, 255),2);
                Imgproc.circle(matImage,middlePoint,3,new Scalar(0, 255, 0, 255),2);
                // write text on frame
                // string of class name of object  // starting point                         // color of text           // size of text
                Imgproc.putText(matImage,labelList.get((int) class_value) + " ("+(int)((left+right)/2)+","+(int)((top+bottom)/2)+") " + score_value*100+"%",new Point(left,top),3,1,new Scalar(255, 0, 0, 255),2);
                Object[] v = new Object[2];
                v[0] = (labelList.get((int) class_value));
                v[1] = ((int)((left+right)/2));
                currentLabels.add(v);
            }

        }

         MainActivity.sendCurrentCameraObjects(currentLabels);
        output_map.clear();
        return matImage;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;

        int quant=0;
        int size_images=INPUTS_SIZE;
        if(quant==0){
            byteBuffer=ByteBuffer.allocateDirect(1*size_images*size_images*3);
        }
        else {
            byteBuffer=ByteBuffer.allocateDirect(4*1*size_images*size_images*3);
        }
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_images*size_images];
        bitmap.getPixels(intValues,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        int pixel=0;

        for (int i=0;i<size_images;++i){
            for (int j=0;j<size_images;++j){
                final  int val=intValues[pixel++];
                if(quant==0){
                    byteBuffer.put((byte) ((val>>16)&0xFF));
                    byteBuffer.put((byte) ((val>>8)&0xFF));
                    byteBuffer.put((byte) (val&0xFF));
                }
                else {
                    byteBuffer.putFloat((((val >> 16) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val >> 8) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val) & 0xFF))/255.0f);
                }
            }
        }
        return byteBuffer;
    }
 /*   private ByteBuffer convertMatToByteBuffer(Mat matImage) {
        ByteBuffer imgData = ByteBuffer.allocateDirect(
                4 * INPUTS_SIZE * INPUTS_SIZE * 3 * floaFile.length);
        imgData.order(ByteOrder.nativeOrder());
        int pixel = 0;
        for (int i = 0; i < INPUTS_SIZE; ++i) {
            for (int j = 0; j < INPUTS_SIZE; ++j) {
                int val;
                int r = (pixel >> 16) &amp; 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = (pixel) & 0xFF;
                val = (r + g + b) / 3;
                imgData.putFloat((((pixel &gt;&gt; 16) &amp; 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((pixel &gt;&gt; 8) &amp; 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((pixel) &amp; 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        return imgData;
    }*/
}
