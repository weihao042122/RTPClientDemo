package com.byd.rtpclientdemo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
//    private static final String IP = "192.168.1.129";
//    private static final int PORT = 1234;
    private Button mStartButton;
    private Button mStopButton;
    private Button h264Button;
    private Button rtpButton;
    private EditText ip_E;
    private EditText port_E;

    private HandlerThread mSendRequestThread;
    private Handler mSendRequestHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mStartButton = findViewById(R.id.start);
        mStartButton.setOnClickListener(this);
        mStopButton = findViewById(R.id.stop);
        mStopButton.setOnClickListener(this);
        h264Button = findViewById(R.id.h264);
        h264Button.setOnClickListener(this);
        rtpButton = findViewById(R.id.rtp);
        rtpButton.setOnClickListener(this);
        ip_E = findViewById(R.id.ip);
        port_E = findViewById(R.id.port);


        mSurface = (SurfaceView) findViewById(R.id.surfaceview1);
        surfacePrepare();

        initHandlerThraed();
    }

    private void initHandlerThraed() {
        mSendRequestThread = new HandlerThread("SendRequest");
        mSendRequestThread.start();
        Looper loop = mSendRequestThread.getLooper();
        mSendRequestHandler = new Handler(loop){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0:
                        Log.i(TAG, "handleMessage start");
                        try {
                            Socket client = new Socket();
                            String IP = ip_E.getText().toString();
                            int PORT = Integer.parseInt(port_E.getText().toString());
                            InetSocketAddress address = new InetSocketAddress(IP, PORT);
                            client.connect(address);
                            OutputStream outputStream = client.getOutputStream();
                            outputStream.write("OpenCamera1".getBytes());
                            client.shutdownOutput();

                            InputStream inputStream = client.getInputStream();
                            byte[] bytes = new byte[1024];
                            int len = inputStream.read(bytes);
                            Log.i(TAG, new String(bytes, 0, len));
                            client.shutdownInput();

                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case 1:
                        Log.i(TAG, "handleMessage stop");
                        try {
                            Socket client = new Socket();
                            String IP = ip_E.getText().toString();
                            int PORT = Integer.parseInt(port_E.getText().toString());
                            InetSocketAddress address = new InetSocketAddress(IP, PORT);
                            client.connect(address);
                            OutputStream outputStream = client.getOutputStream();
                            outputStream.write("CloseCamera1".getBytes());
                            client.shutdownOutput();

                            InputStream inputStream = client.getInputStream();
                            byte[] bytes = new byte[1024];
                            int len = inputStream.read(bytes);
                            Log.i(TAG, new String(bytes, 0, len));
                            client.shutdownInput();

                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.start) {
            Log.i(TAG, "onClick start");
            mSendRequestHandler.sendEmptyMessage(0);
        } else if (v.getId() == R.id.stop) {
            Log.i(TAG, "onClick stop");
            mSendRequestHandler.sendEmptyMessage(1);
        } else if (v.getId() == R.id.h264) {
            Log.i(TAG, "onClick h264");
//            startActivity(new Intent(MainActivity.this, LocalH264Activity.class));
            changeVideoSize(mSurface, VIDEO_WIDTH, VIDEO_HEIGHT);

            h264Init();
        }else if (v.getId() == R.id.rtp){
            rtpStart();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSendRequestThread != null) {
            mSendRequestThread.quit();
        }
    }



    private SurfaceView mSurface = null;
    private SurfaceHolder mSurfaceHolder;
    private Thread mDecodeThread;
    private MediaCodec mCodec;
    private boolean mStopFlag = false;
    private DataInputStream mInputStream;
    private String FileName = "test.h264";
    private static final int VIDEO_WIDTH = 1281;
    private static final int VIDEO_HEIGHT = 534;
    private int FrameRate = 25;
    private Boolean UseSPSandPPS = false;
    private String filePath = Environment.getExternalStorageDirectory() + "/" + FileName;

    private void h264Init(){
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        File f = new File(filePath);
        if (null == f || !f.exists() || f.length() == 0) {
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            //获取文件输入流
            mInputStream = new DataInputStream(new FileInputStream(new File(filePath)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            try {
                mInputStream.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        startDecodingThread();
    }

    public void changeVideoSize(SurfaceView mSurfaceView, int videoWidth, int videoHeight) {
        //视频容器宽度以400像素计算比例
        float surfaceWidth = mSurfaceView.getWidth();
        float max = (float) videoWidth / (float) surfaceWidth;

        //视频宽高分别/最大倍数值 计算出放大后的视频尺寸
        videoWidth = (int) Math.ceil((float) videoWidth / max);
        videoHeight = (int) Math.ceil((float) videoHeight / max);

        //无法直接设置视频尺寸，将计算出的视频尺寸设置到surfaceView 让视频自动填充。
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(videoWidth, videoHeight);

        lp.gravity = Gravity.CENTER;
        mSurfaceView.setLayoutParams(lp);
    }

    private void surfacePrepare(){
        mSurfaceHolder = mSurface.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try

                {
                    //通过多媒体格式名创建一个可用的解码器
                    mCodec = MediaCodec.createDecoderByType("video/avc");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //初始化编码器
                final MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc", VIDEO_WIDTH, VIDEO_HEIGHT);
                //获取h264中的pps及sps数据
                if (UseSPSandPPS) {
                    byte[] header_sps = {0, 0, 0, 1, 103, 66, 0, 42, (byte) 149, (byte) 168, 30, 0, (byte) 137, (byte) 249, 102, (byte) 224, 32, 32, 32, 64};
                    byte[] header_pps = {0, 0, 0, 1, 104, (byte) 206, 60, (byte) 128, 0, 0, 0, 1, 6, (byte) 229, 1, (byte) 151, (byte) 128};
                    mediaformat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                    mediaformat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
                }
                //设置帧率
                mediaformat.setInteger(MediaFormat.KEY_FRAME_RATE, FrameRate);
                //https://developer.android.com/reference/android/media/MediaFormat.html#KEY_MAX_INPUT_SIZE
                //设置配置参数，参数介绍 ：
                // format	如果为解码器，此处表示输入数据的格式；如果为编码器，此处表示输出数据的格式。
                //surface	指定一个surface，可用作decode的输出渲染。
                //crypto	如果需要给媒体数据加密，此处指定一个crypto类.
                //   flags	如果正在配置的对象是用作编码器，此处加上CONFIGURE_FLAG_ENCODE 标签。
                mCodec.configure(mediaformat, holder.getSurface(), null, 0);
//                startDecodingThread();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mCodec.stop();
                mCodec.release();
            }
        });
    }
    private void startDecodingThread() {
        mCodec.start();
        mDecodeThread = new Thread(new decodeThread());
        mDecodeThread.start();
    }

    /**
     * @author ldm
     * @description 解码线程
     * @time 2016/12/19 16:36
     */
    private class decodeThread implements Runnable {
        @Override
        public void run() {
            try {
                decodeLoop();
            } catch (Exception e) {
            }
        }
        private void printHead20(byte[] input, int offset, int len){
            int l = len;
            int i = 0;
            String t = "frameLen="+l+", offset="+offset+"--";
            for(i = offset; i < offset+10  && i < input.length; i++){
                t += Integer.toHexString(input[i]&0xff)+" ";
            }
            t+= " ... ";
            for(i = offset+len-5; i < offset+len  && i < input.length; i++){
                t += Integer.toHexString(input[i]&0xff)+" ";
            }
            Log.d("cwh", t);
        }
        private void decodeLoop() {
            //存放目标文件的数据
            ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
            //解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            long timeoutUs = 10000;
            byte[] marker0 = new byte[]{0, 0, 0, 1};
            byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
            byte[] streamBuffer = null;
            try {
                streamBuffer = getBytes(mInputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int bytes_cnt = 0;
            byte[] mH264Data = new byte[200000];

            while (mStopFlag == false) {
                bytes_cnt = streamBuffer.length;
                if (bytes_cnt == 0) {
                    streamBuffer = dummyFrame;
                }

                int startIndex = 0;
                int remaining = bytes_cnt;
                while (true) {
                    if (remaining == 0 || startIndex >= remaining) {
                        break;
                    }
                    int nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
                    if (nextFrameStart == -1) {
                        nextFrameStart = remaining;
                    } else {
                    }
//                    printHead20(streamBuffer, startIndex,nextFrameStart - startIndex);
                    System.arraycopy(streamBuffer, startIndex, mH264Data, 0, nextFrameStart - startIndex);
                    printHead20(mH264Data, 0,nextFrameStart - startIndex);

                    int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                    if (inIndex >= 0) {
                        ByteBuffer byteBuffer = inputBuffers[inIndex];
                        byteBuffer.clear();
//                        byteBuffer.put(streamBuffer, startIndex, nextFrameStart - startIndex);
                        byteBuffer.put(mH264Data, 0, nextFrameStart - startIndex);
                        //在给指定Index的inputbuffer[]填充数据后，调用这个函数把数据传给解码器
                        mCodec.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
                        startIndex = nextFrameStart;
                    } else {
                        continue;
                    }

                    int outIndex = mCodec.dequeueOutputBuffer(info, timeoutUs);
                    if (outIndex >= 0) {
                        //帧控制是不在这种情况下工作，因为没有PTS H264是可用的
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        boolean doRender = (info.size != 0);
                        //对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
                        mCodec.releaseOutputBuffer(outIndex, doRender);
                    } else {
                    }
                }
                mStopFlag = true;
            }
        }
    }

    public static byte[] getBytes(InputStream is) throws IOException {
        int len;
        int size = 1024;
        byte[] buf;
        if (is instanceof ByteArrayInputStream) {
            size = is.available();
            buf = new byte[size];
            len = is.read(buf, 0, size);
        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            buf = new byte[size];
            while ((len = is.read(buf, 0, size)) != -1)
                bos.write(buf, 0, len);
            buf = bos.toByteArray();
        }
        return buf;
    }

    int KMPMatch(byte[] pattern, byte[] bytes, int start, int remain) {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int[] lsp = computeLspTable(pattern);

        int j = 0;  // Number of chars matched in pattern
        for (int i = start; i < remain; i++) {
            while (j > 0 && bytes[i] != pattern[j]) {
                // Fall back in the pattern
                j = lsp[j - 1];  // Strictly decreasing
            }
            if (bytes[i] == pattern[j]) {
                // Next char matched, increment position
                j++;
                if (j == pattern.length)
                    return i - (j - 1);
            }
        }

        return -1;  // Not found
    }

    int[] computeLspTable(byte[] pattern) {
        int[] lsp = new int[pattern.length];
        lsp[0] = 0;  // Base case
        for (int i = 1; i < pattern.length; i++) {
            // Start by assuming we're extending the previous LSP
            int j = lsp[i - 1];
            while (j > 0 && pattern[i] != pattern[j])
                j = lsp[j - 1];
            if (pattern[i] == pattern[j])
                j++;
            lsp[i] = j;
        }
        return lsp;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    private byte[] mH264Data = new byte[200000];
    private DatagramSocket mSocket;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void rtpStart(){
        changeVideoSize(mSurface, VIDEO_WIDTH, VIDEO_HEIGHT);
        try {
            int PORT = Integer.parseInt(port_E.getText().toString());
            mSocket = new DatagramSocket(PORT);
            mSocket.setReuseAddress(true);
            mSocket.setBroadcast(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new PreviewThread(mCodec);
    }

    //处理线程
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private class PreviewThread extends Thread {
        DatagramPacket datagramPacket = null;
        MediaCodec mCodec;
        //存放目标文件的数据
        ByteBuffer[] inputBuffers;
        //解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
        MediaCodec.BufferInfo info;
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        public PreviewThread(MediaCodec tCodec) {
            mCodec = tCodec;
            mCodec.start();
            start();
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            boolean isFiset = true;
            int pre_seq_num = 0;
            int destPos = 0;
            int h264Length;
            inputBuffers = mCodec.getInputBuffers();
            //解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
            info = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            while (true) {
                if (mSocket != null) {
                    try {
                        byte[] data = new byte[1500];
                        datagramPacket = new DatagramPacket(data, data.length);
                        mSocket.receive(datagramPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                byte[] rtpData = datagramPacket.getData();
                if (rtpData != null) {
                    int l3 = (rtpData[12] << 24) & 0xff000000;
                    int l4 = (rtpData[13] << 16) & 0x00ff0000;
                    int l5 = (rtpData[14] << 8) & 0x0000ff00;
                    int l6 = rtpData[15] & 0x000000FF;
//                    h264Length = rtpData.length - 12;
                    h264Length = l3 + l4 + l5 + l6;
                    Log.i("cwh", "run: h264Length = " + h264Length);

                    byte[] snm = new byte[2];
                    System.arraycopy(rtpData, 2, snm, 0, 2);
                    int seq_num = CalculateUtil.byte2short(snm);
                    Log.i(TAG, "seq_num = " + seq_num);

                    int timeStamp1 = (rtpData[4] << 24) & 0xff000000;
                    int timeStamp2 = (rtpData[5] << 16) & 0x00ff0000;
                    int timeStamp3 = (rtpData[6] << 8) & 0x0000ff00;
                    int timeStamp4 = rtpData[7] & 0x000000FF;
                    int timeStamp = timeStamp1 + timeStamp2 + timeStamp3 + timeStamp4;
                    Log.i(TAG, "timeStamp = " + timeStamp);

                    if (isFiset) {
                        pre_seq_num = seq_num;
                        isFiset = false;
                    } else {
                        if (seq_num - pre_seq_num > 1) {
                            Log.i(TAG, "Packet loss" + (seq_num - pre_seq_num));
                        } else if (seq_num - pre_seq_num < 1) {
                            Log.i(TAG, "Out of order packets" + (seq_num - pre_seq_num));
                        }
                        pre_seq_num = seq_num;
                    }

                    byte[] marker0 = new byte[]{0, 0, 0, 1};
                    byte indicatorType = (byte) (CalculateUtil.byteToInt(rtpData[16]) & 0x1f);
                    Log.i(TAG, "indicatorType = " + indicatorType);
                    if (indicatorType == 28) {
                        byte s = (byte) (rtpData[17] & 0x80);
                        byte e = (byte) (rtpData[17] & 0x40);
                        Log.i(TAG, "s = " + s + "; e = " + e);

                        if (s == -128) {        // frist packet
                            System.arraycopy(marker0, 0, mH264Data, 0, 4);
                            destPos =4;
                            int t = (rtpData[16]&0x60) | (rtpData[17]&0x1f);
                            rtpData[17] = (byte)t;
                            System.arraycopy(rtpData, 17, mH264Data, destPos, h264Length-1);
                            destPos += h264Length-1;
                        } else if (e == 64) {   // end packet
                            System.arraycopy(rtpData, 18, mH264Data, destPos, h264Length-2);
                            destPos += h264Length-2;
                            offerDecoder(mH264Data, destPos);
                            destPos = 0;
                            CalculateUtil.memset(mH264Data, 0, mH264Data.length);
                        } else {
                            System.arraycopy(rtpData, 18, mH264Data, destPos, h264Length-2);
                            destPos += h264Length-2;
                        }
                    } else {
                        System.arraycopy(marker0, 0, mH264Data, 0, 4);
                        destPos =4;
                        System.arraycopy(rtpData, 16, mH264Data, destPos, h264Length);
                        offerDecoder(mH264Data, destPos+h264Length);
                        CalculateUtil.memset(mH264Data, 0, mH264Data.length);
                        destPos = 0;
                    }

                }
            }
        }

        private void printHead20(byte[] input, int len){
            int l = len;
            int i = 0;
            String t = "frameLen="+l+"--";
            for(i = 0; i < 10  && i < l; i++){
                t += Integer.toHexString(input[i]&0xff)+" ";
            }
            t+= " ... ";
            for(i = len-5; i < len  && i < input.length; i++){
                t += Integer.toHexString(input[i]&0xff)+" ";
            }
            Log.d("cwh", t);
        }
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        private void offerDecoder(byte[] input, int length) {
            printHead20(input, length);
            int inIndex = mCodec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer byteBuffer = inputBuffers[inIndex];
                byteBuffer.clear();
                byteBuffer.put(input, 0, length);
                //在给指定Index的inputbuffer[]填充数据后，调用这个函数把数据传给解码器
                mCodec.queueInputBuffer(inIndex, 0, length, 0, 0);
            } else {
                Log.d("cwh", "dequeueInputBuffer ret<0");
                return;
            }

            int outIndex = mCodec.dequeueOutputBuffer(info, 1000);
            if (outIndex >= 0) {
//                while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
//                    try {
//                        Thread.sleep(100);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
                boolean doRender = (info.size != 0);
                //对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
                mCodec.releaseOutputBuffer(outIndex, doRender);
            } else {
                Log.d("cwh", "dequeueOutputBuffer ret<0");
            }
        }
    }
}
