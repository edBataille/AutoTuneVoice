package br.com.autotunevoice;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int SAMPLE_RATE=44100, REQ_MIC=10;
    private TextView txtInput,txtStatus,txtTimer,txtStrength;
    private Button btnRecord,btnStop,btnPlayOriginal,btnPlayCorrected;
    private Spinner spinnerScale; private SeekBar seekStrength;
    private VuMeterView vuMeter;
    private AudioRecord recorder; private volatile boolean recording;
    private AudioDeviceInfo preferredInputDevice;
    private AudioDeviceCallback deviceCallback;
    private File rawPcm,originalWav,correctedWav; private MediaPlayer player;
    private long startedAt; private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);
        txtInput=findViewById(R.id.txtInput);txtStatus=findViewById(R.id.txtStatus);txtTimer=findViewById(R.id.txtTimer);txtStrength=findViewById(R.id.txtStrength);
        btnRecord=findViewById(R.id.btnRecord);btnStop=findViewById(R.id.btnStop);btnPlayOriginal=findViewById(R.id.btnPlayOriginal);btnPlayCorrected=findViewById(R.id.btnPlayCorrected);
        spinnerScale=findViewById(R.id.spinnerScale);seekStrength=findViewById(R.id.seekStrength);vuMeter=findViewById(R.id.vuMeter);
        List<String> scales=new ArrayList<>();scales.add("Cromática — qualquer nota");
        String[] roots={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
        for(String r:roots){scales.add(r+" maior");scales.add(r+" menor");}
        spinnerScale.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,scales));
        seekStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){txtStrength.setText(p+"% — "+(p<35?"correção suave":p<70?"correção média":"correção forte"));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        btnRecord.setOnClickListener(v->ensurePermissionAndStart()); btnStop.setOnClickListener(v->stopRecording());
        btnPlayOriginal.setOnClickListener(v->play(originalWav));btnPlayCorrected.setOnClickListener(v->play(correctedWav));
        updateInputLabel();
        // App "eclético" na entrada de áudio: reage na hora se o usuário plugar ou tirar
        // um microfone (P2 ou USB-C) enquanto o app está aberto.
        deviceCallback=new AudioDeviceCallback(){
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added){updateInputLabel();}
            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed){updateInputLabel();}
        };
        ((AudioManager)getSystemService(AUDIO_SERVICE)).registerAudioDeviceCallback(deviceCallback,handler);
    }

    /**
     * Detecta qual entrada de áudio usar: microfone USB-C, microfone com fio (plugue P2)
     * ou o microfone do próprio celular, nessa ordem de prioridade. Guarda o AudioDeviceInfo
     * escolhido em preferredInputDevice para que a gravação seja roteada explicitamente para ele.
     */
    private void updateInputLabel(){AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
        String label="Microfone do celular";AudioDeviceInfo chosen=null;
        for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_INPUTS)){
            int t=d.getType();
            if(t==AudioDeviceInfo.TYPE_USB_DEVICE||t==AudioDeviceInfo.TYPE_USB_HEADSET){label="Microfone USB-C: "+d.getProductName();chosen=d;break;}
            if(t==AudioDeviceInfo.TYPE_WIRED_HEADSET&&chosen==null){label="Microfone com fio (plugue P2): "+d.getProductName();chosen=d;}
        }
        preferredInputDevice=chosen;
        txtInput.setText("Entrada de áudio: "+label);
    }
    private void ensurePermissionAndStart(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);}else startRecording();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_MIC&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startRecording();else toast("A permissão do microfone é necessária.");}

    private void startRecording(){
        updateInputLabel(); // reconfirma a entrada (celular, P2 ou USB-C) bem antes de começar a gravar
        int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);int size=Math.max(min,8192);
        recorder=new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);
        if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){recorder.release();recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);}
        if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){
            toast("Não foi possível iniciar a gravação com a entrada de áudio atual.");
            recorder.release();recorder=null;btnRecord.setEnabled(true);btnStop.setEnabled(false);return;
        }
        // Entrada "eclética": se houver um microfone P2 ou USB-C plugado, a gravação é
        // roteada explicitamente para ele; senão, usa-se o microfone do celular normalmente.
        if(preferredInputDevice!=null) recorder.setPreferredDevice(preferredInputDevice);
        rawPcm=new File(getCacheDir(),"capture.pcm");recording=true;startedAt=System.currentTimeMillis();btnRecord.setEnabled(false);btnStop.setEnabled(true);btnPlayOriginal.setEnabled(false);btnPlayCorrected.setEnabled(false);txtStatus.setText("Gravando...");
        recorder.startRecording();executor.execute(()->recordLoop(size));handler.post(timerTick);
    }
    private void recordLoop(int size){byte[] buf=new byte[size];try(FileOutputStream out=new FileOutputStream(rawPcm)){while(recording){int n=recorder.read(buf,0,buf.length);if(n>0){out.write(buf,0,n);updateVuLevel(buf,n);}}}catch(Exception e){runOnUiThread(()->txtStatus.setText("Erro de gravação: "+e.getMessage()));}finally{handler.post(()->vuMeter.setLevel(0f));}}

    /** Calcula o nível RMS do trecho de áudio recém-lido e atualiza o VU meter na tela. */
    private void updateVuLevel(byte[] buf,int n){
        int samples=n/2;if(samples<=0)return;
        long sumSquares=0;
        for(int i=0;i<samples;i++){
            int lo=buf[i*2]&0xFF,hi=buf[i*2+1];
            short s=(short)((hi<<8)|lo);
            sumSquares+=(long)s*s;
        }
        double rms=Math.sqrt((double)sumSquares/samples);
        float level=(float)Math.min(1.0,rms/12000.0); // 12000 dá folga antes de "estourar" no vermelho
        handler.post(()->vuMeter.setLevel(level));
    }
    private final Runnable timerTick=new Runnable(){public void run(){if(!recording)return;long sec=(System.currentTimeMillis()-startedAt)/1000;txtTimer.setText(String.format(Locale.getDefault(),"%02d:%02d",sec/60,sec%60));handler.postDelayed(this,500);}};

    private void stopRecording(){recording=false;btnStop.setEnabled(false);txtStatus.setText("Preparando o áudio...");vuMeter.setLevel(0f);try{recorder.stop();recorder.release();}catch(Exception ignored){}recorder=null;
        executor.execute(()->{try{Thread.sleep(150);short[] pcm=readRaw(rawPcm);String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());originalWav=new File(getCacheDir(),"voz_original_"+stamp+".wav");correctedWav=new File(getCacheDir(),"voz_corrigida_"+stamp+".wav");WavIO.writeMono16(originalWav,pcm,SAMPLE_RATE);
            runOnUiThread(()->txtStatus.setText("Corrigindo a afinação..."));String scale=(String)spinnerScale.getSelectedItem();float strength=seekStrength.getProgress()/100f;short[] fixed=PitchCorrector.process(pcm,SAMPLE_RATE,scale,strength);WavIO.writeMono16(correctedWav,fixed,SAMPLE_RATE);
            saveToMusic(originalWav);saveToMusic(correctedWav);runOnUiThread(()->{txtStatus.setText("Concluído. Original e corrigido foram salvos.");btnRecord.setEnabled(true);btnPlayOriginal.setEnabled(true);btnPlayCorrected.setEnabled(true);});
        }catch(Exception e){runOnUiThread(()->{txtStatus.setText("Erro: "+e.getMessage());btnRecord.setEnabled(true);});}});
    }
    private short[] readRaw(File f)throws IOException{byte[] bytes=new byte[(int)f.length()];try(FileInputStream in=new FileInputStream(f)){int off=0,n;while(off<bytes.length&&(n=in.read(bytes,off,bytes.length-off))>0)off+=n;}short[] s=new short[bytes.length/2];for(int i=0;i<s.length;i++)s[i]=(short)((bytes[i*2]&255)|(bytes[i*2+1]<<8));return s;}
    private void saveToMusic(File source)throws IOException{ContentValues v=new ContentValues();v.put(MediaStore.Audio.Media.DISPLAY_NAME,source.getName());v.put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");v.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/AutoTuneVoice");Uri uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("Não foi possível criar o arquivo.");try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}}
    private void play(File f){if(f==null)return;try{if(player!=null){player.stop();player.release();}player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.prepare();player.start();txtStatus.setText("Reproduzindo: "+f.getName());}catch(Exception e){toast("Não foi possível reproduzir: "+e.getMessage());}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){recording=false;if(player!=null)player.release();if(deviceCallback!=null)((AudioManager)getSystemService(AUDIO_SERVICE)).unregisterAudioDeviceCallback(deviceCallback);executor.shutdownNow();super.onDestroy();}
}
