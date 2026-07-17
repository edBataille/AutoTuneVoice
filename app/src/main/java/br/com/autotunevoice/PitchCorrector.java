package br.com.autotunevoice;

import java.util.Arrays;

/**
 * Corretor offline leve, sem bibliotecas externas. Usa detecção por autocorrelação
 * e overlap-add granular. É uma primeira versão experimental, pensada para voz isolada.
 */
public final class PitchCorrector {
    private PitchCorrector() {}
    private static final int[] CHROMATIC = {0,1,2,3,4,5,6,7,8,9,10,11};
    private static final int[] MAJOR = {0,2,4,5,7,9,11};
    private static final int[] MINOR = {0,2,3,5,7,8,10};
    private static final String[] ROOTS = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};

    public static short[] process(short[] input, int sampleRate, String scaleName, float strength) {
        if (input.length < 4096 || strength <= 0.01f) return Arrays.copyOf(input, input.length);
        int frame = 2048, hop = 512;
        float[] out = new float[input.length + frame];
        float[] weight = new float[out.length];
        float[] window = new float[frame];
        for (int i=0;i<frame;i++) window[i]=(float)(0.5-0.5*Math.cos(2*Math.PI*i/(frame-1)));

        for (int start=0; start+frame<input.length; start+=hop) {
            double hz = detectPitch(input, start, frame, sampleRate);
            double ratio = 1.0;
            if (hz >= 70 && hz <= 1000) {
                double midi = 69.0 + 12.0 * log2(hz / 440.0);
                double target = nearestAllowedMidi(midi, scaleName);
                target = midi + (target-midi)*strength;
                ratio = Math.pow(2.0, (target-midi)/12.0);
                ratio = Math.max(0.80, Math.min(1.25, ratio));
            }
            for (int i=0;i<frame;i++) {
                double src = i * ratio;
                int p = (int)src;
                double frac = src-p;
                float sample = 0;
                if (p>=0 && p+1<frame) sample=(float)(input[start+p]*(1-frac)+input[start+p+1]*frac);
                int dst=start+i;
                out[dst]+=sample*window[i];
                weight[dst]+=window[i];
            }
        }
        short[] result=new short[input.length];
        for(int i=0;i<result.length;i++){
            float v=weight[i]>0.001f?out[i]/weight[i]:input[i];
            result[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(v)));
        }
        return result;
    }

    private static double detectPitch(short[] x,int start,int n,int sr){
        double rms=0; for(int i=0;i<n;i++){double v=x[start+i];rms+=v*v;} rms=Math.sqrt(rms/n);
        if(rms<350) return -1;
        int minLag=Math.max(1,sr/1000), maxLag=Math.min(n/2,sr/70);
        double best=-1; int bestLag=-1;
        for(int lag=minLag;lag<=maxLag;lag++){
            double sum=0,a=0,b=0;
            for(int i=0;i<n-lag;i++){double u=x[start+i],v=x[start+i+lag];sum+=u*v;a+=u*u;b+=v*v;}
            double corr=sum/(Math.sqrt(a*b)+1e-9);
            if(corr>best){best=corr;bestLag=lag;}
        }
        return best>0.45 && bestLag>0 ? (double)sr/bestLag : -1;
    }

    private static double nearestAllowedMidi(double midi,String name){
        int[] notes=CHROMATIC; int root=0;
        if(!name.startsWith("Cromática")){
            String rootName=name.substring(0,name.indexOf(' '));
            for(int i=0;i<ROOTS.length;i++) if(ROOTS[i].equals(rootName)) root=i;
            notes=name.contains("menor")?MINOR:MAJOR;
        }
        double best=midi,dist=999;
        int center=(int)Math.round(midi);
        for(int candidate=center-12;candidate<=center+12;candidate++){
            int pc=((candidate-root)%12+12)%12;
            boolean ok=false; for(int note:notes) if(pc==note){ok=true;break;}
            if(ok && Math.abs(candidate-midi)<dist){dist=Math.abs(candidate-midi);best=candidate;}
        }
        return best;
    }
    private static double log2(double v){return Math.log(v)/Math.log(2.0);}
}
