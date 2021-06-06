package com.example.facerecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Pair;
import android.util.Size;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int MY_DATA_CHECK_CODE = 1;
    FaceDetector detector;
    PreviewView previewView;
    ImageView face_preview;
    Interpreter tfLite;
    TextView reco_name, preview_info;
    Button recognize, actions, camera_switch;
    ImageButton add_face;
    CameraSelector cameraSelector;
    Context context = MainActivity.this;
    ProcessCameraProvider cameraProvider;
    int contador;

    String modelFile="mobile_face_net.tflite"; //nombre del modelo

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    boolean start = true, flipX = false;
    int cam_face = CameraSelector.LENS_FACING_BACK; //Por defecto la aplicacion se inicializa con camara trasera
    int[] intValues;
    int inputSize = 112;  //Tamaño de entrada del modelo
    int OUTPUT_SIZE = 192; //Tamaño de salida del modelo

    boolean isModelQuantized=false;
    float[][] embeedings;
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    private static int SELECT_PICTURE = 1;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>(); //Personas registradas
    private String msg = "";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registered = readFromSP();

        setContentView(R.layout.activity_main);

        face_preview = findViewById(R.id.imageView);
        reco_name = findViewById(R.id.textView);
        preview_info = findViewById(R.id.textView2);
        add_face = findViewById(R.id.imageButton);
        add_face.setVisibility(View.INVISIBLE);

        face_preview.setVisibility(View.INVISIBLE);

        recognize = findViewById(R.id.button3);
        camera_switch = findViewById(R.id.button00);
        actions = findViewById(R.id.button2);

        preview_info.setText("");

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);

        //Permisos Acceso a la camara
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }

        //Menu de opciones
        actions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder opciones = new AlertDialog.Builder(context);
                opciones.setTitle("Menu de opciones:");
                opciones.setIcon(R.drawable.face_icon2);
                String[] opcion= {
                        "Ver la lista de Registros",
                        "Actualizar lista de registros",
                        "Guardar Registros",
                        "Cargar todos lode registros",
                        "Eliminar todos los registros",
                        "Subir un archivo local"};
                opciones.setItems(opcion, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                displaynameListview();
                                break;
                            case 1:
                                updatenameListview();
                                break;
                            case 2:
                                insertToSP(registered,false);
                                break;
                            case 3:
                                registered.putAll(readFromSP());
                                break;
                            case 4:
                                clearnameList();
                                break;
                            case 5:
                                loadphoto();
                                break;
                        }

                    }
                });

                AlertDialog dialogoOpciones = opciones.create();
                dialogoOpciones.show();
            }
        });

        //Cambio de Camara
        camera_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cam_face == CameraSelector.LENS_FACING_BACK) {
                    cam_face = CameraSelector.LENS_FACING_FRONT;
                    flipX = true;
                }
                else {
                    cam_face = CameraSelector.LENS_FACING_BACK;
                    flipX = false;
                }
                cameraProvider.unbindAll();
                cameraBind();
            }
        });

        add_face.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addFace();
            }
        }));

        recognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recognize.getText().toString().equals("Reconocer")) {
                    start = true;
                    recognize.setText("Añadir registro");
                    add_face.setVisibility(View.INVISIBLE);
                    reco_name.setVisibility(View.VISIBLE);
                    face_preview.setVisibility(View.INVISIBLE);
                    preview_info.setText("\n Persona reconocida: ");
                    preview_info.setVisibility(View.INVISIBLE);
                }else {
                    recognize.setText("Reconocer");
                    add_face.setVisibility(View.VISIBLE);
                    reco_name.setVisibility(View.INVISIBLE);
                    face_preview.setVisibility(View.VISIBLE);
                    preview_info.setText(
                            "\nInstrucciones \n\n" +
                            "1. Colocar el rostro en frente.\n\n" +
                            "2. Una vez reconocido su rostro aparecera aca.\n\n" +
                            "3. Guardar");
                }
            }
        });

        //Cargar modelo y registros
        try {
            tfLite = new Interpreter(loadModelFile(MainActivity.this,modelFile));
        }catch (IOException e) {
            e.printStackTrace();
        }

        //Detector inicializado
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);

        cameraBind();
    }

    private void addFace(){
        start = false;
        final EditText input = new EditText(context);

        AlertDialog.Builder add = new AlertDialog.Builder(context);
        add.setTitle("Añadir nuevo Registro");
        add.setIcon(R.drawable.face_icon2);

        input.setInputType(InputType.TYPE_CLASS_TEXT);
        add.setMessage("Ingrese el nombre del registro: ");
        add.setView(input);
        input.setPadding(50,10,50,20);


        add.setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition("0", "", -1f);
                result.setExtra(embeedings);

                registered.put(input.getText().toString(), result);
                start = true;

            }
        });
        add.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                start = true;
                dialog.cancel();
            }
        });

        add.show();
    }

    private  void clearnameList(){
        AlertDialog.Builder delete =new AlertDialog.Builder(context);
        delete.setTitle("Eliminar Registros");
        delete.setIcon(R.drawable.face_icon2);
        delete.setMessage("Esta seguro que desea eliminar todos los registros?");
        delete.setPositiveButton("Confirmar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                registered.clear();
                Toast.makeText(context, "Registros eliminados", Toast.LENGTH_SHORT).show();
            }
        });
        insertToSP(registered,true);
        delete.setNegativeButton("Cancelar",null);
        AlertDialog dialogoEliminar = delete.create();
        dialogoEliminar.show();
    }

    private void updatenameListview(){
        AlertDialog.Builder update = new AlertDialog.Builder(context);
        update.setIcon(R.drawable.face_icon2);
        if(registered.isEmpty()) {
            update.setTitle("Status de Registros");
            update.setMessage("No hay registros existentes para actualizar");
            update.setPositiveButton("OK",null);
        }else {
            update.setTitle("Seleccione Registro a eliminar:");
        }
        String[] registros= new String[registered.size()];
        boolean[] itemsElegidos = new boolean[registered.size()];
        int i=0;

        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()){
            registros[i] = entry.getKey();
            itemsElegidos[i]=false;
            i = i + 1;
        }

        update.setMultiChoiceItems(registros, itemsElegidos, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item, boolean isChecked) {
                Toast.makeText(MainActivity.this, registros[item], Toast.LENGTH_SHORT).show();
                itemsElegidos[item]=isChecked;
            }
        });

        update.setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for(int i=0;i<itemsElegidos.length;i++){
                    //System.out.println("status:"+checkedItems[i]);
                    if(itemsElegidos[i]){
                        Toast.makeText(MainActivity.this, registros[i], Toast.LENGTH_SHORT).show();
                        registered.remove(registros[i]);
                    }
                }
                Toast.makeText(context, "Registros Actualizados", Toast.LENGTH_SHORT).show();
            }
        });
        update.setNegativeButton("Cancelar", null);

        AlertDialog dialogoUpdate = update.create();
        dialogoUpdate.show();
    }

    private void displaynameListview(){
        AlertDialog.Builder mostrar = new AlertDialog.Builder(context);
        mostrar.setIcon(R.drawable.face_icon2);
        mostrar.setTitle("Listado de Registrados");

        if(registered.isEmpty()) {
            mostrar.setTitle("Status de Registros");
            mostrar.setMessage("No hay registros existentes para mostrar");
        }else {
            mostrar.setTitle("Listado de Registros");
        }

        String[] registros =  new String[registered.size()];
        boolean[] itemsElegidos = new boolean[registered.size()];
        int i=0;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            registros[i]=entry.getKey();
            itemsElegidos[i]=false;
            i=i+1;
        }
        mostrar.setItems(registros,null);

        mostrar.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        AlertDialog dialogoMostrar = mostrar.create();
        dialogoMostrar.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Acceso permitido", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Acceso denegado", Toast.LENGTH_LONG).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //Capturar el rostro y colocarlo en el cuadro inferior
    private void cameraBind(){
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        previewView = findViewById(R.id.previewView);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {

            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cam_face)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        //El ultimo rostro reconocido lo muestra
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {

                InputImage image = null;

                @SuppressLint("UnsafeExperimentalUsageError")

                // Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)

                Image mediaImage = imageProxy.getImage();

                if (mediaImage != null) {
                    image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                }

                //Process acquired image to detect faces
                Task<List<Face>> result = detector.process(image).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                if(faces.size()!=0) {
                                    Face face = faces.get(0); //Get first face from detected faces

                                    //Imagen --> Bitmap
                                    Bitmap frame_bmp = toBitmap(mediaImage);

                                    int rot = imageProxy.getImageInfo().getRotationDegrees();

                                    //Ajustar la orientacion del rostro
                                    Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, flipX, false);

                                    //Bouding box
                                    RectF boundingBox = new RectF(face.getBoundingBox());

                                    //Cortar el bouding box del bitmap
                                    Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                    //Renderizamos el rostro para que este del tamaño del modelo 112*112
                                    Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);

                                    if(start)
                                        //Enviar imagen renderizada bitmap para crear el rostro
                                        recognizeImage(scaled);
                                        try {
                                            Thread.sleep(100);  //La camara se refresca cada 100
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) { }
                            })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<Face>> task) {

                                imageProxy.close(); //important to acquire next frame for analysis
                            }
                        });
            }
        });

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);

    }

    public void recognizeImage(final Bitmap bitmap) {
        // Colocar la imagen en el cuadro de preview
        face_preview.setImageBitmap(bitmap);

        //Creamos el ByteBuffer para la normalizacion de la imagen
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        //Obtenemos los valores de los pixeles de; bitmap para normalizar la imagen
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
        //imgData es la entrada del modelo
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();


        embeedings = new float[1][OUTPUT_SIZE]; //Salida del modelo almacenado en una variable

        outputMap.put(0, embeedings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap); //Hacemos correr el modelo

        float distance = Float.MAX_VALUE;
        String id = "0";
        String label = "?";

        //Comparacion de registros
        if (registered.size() > 0) {
            final Pair<String, Float> nearest = findNearest(embeedings[0]);//Find closest matching face

            if (nearest != null) {
                final String name = nearest.first;
                label = name;
                distance = nearest.second;
                //Si la distancia entre los registros es mas de 1000, saldra Rostro desconocido
                if(distance<1.000f) {
                    reco_name.setText(name + "\nACCESO PERMITIDO");
                    msg = "PERMITIDO";
                    contador++;
                }
                else {
                    reco_name.setText("Persona desconocida\nACCESO DENEGADO");
                    msg = "DENEGADO";
                    contador++;
                }
            }
        }
        if(contador==5){
            mTts.speak(msg , TextToSpeech.QUEUE_FLUSH, null);
            contador = 0;
        }
        final int numDetectionsOutput = 1;
        final ArrayList<SimilarityClassifier.Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
        SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(id, label, distance);
        recognitions.add( rec );
    }

    //Comparacion de registros
    private Pair<String, Float> findNearest(float[] emb) {
        Pair<String, Float> ret = null;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff*diff;
            }

            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }
        return ret;
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // Creamos la matriz de manipulacion
        Matrix matrix = new Matrix();

        // Configuramos el tamaño del bitmap
        matrix.postScale(scaleWidth, scaleHeight);

        // Creamos el nuevo bitmap
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(),
                Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect( new RectF(0, 0, cropRectF.width(), cropRectF.height()), paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotamos la matriz
        matrix.postRotate(rotationDegrees);

        // Imagen en modo espejo para el eje x y eje y.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Reciclamos el ultimo bitmap
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }


    //Conversion de la imagen a la nomenclatura del modelo
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride;
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {}

            // Debemos guardar en U y en V pixel por pixel
            vBuffer.put(1, savePixel);
        }

        for (int row=0; row<height/2; row++){
            for (int col=0; col<width/2; col++){
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private Bitmap toBitmap(Image image) {

        byte[] nv21=YUV_420_888toNV21(image);


        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();

        System.out.println("FORMATO DE LA IMG"+image.getFormat());

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    //Guardar registros en el archivo
    private void insertToSP(HashMap<String, SimilarityClassifier.Recognition> jsonMap,boolean clear) {
        if(clear) {
            jsonMap.clear();
        }else{
            jsonMap.putAll(readFromSP());
        }

        String jsonString = new Gson().toJson(jsonMap);

        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);

        editor.apply();
        Toast.makeText(context, "Registros Guardados", Toast.LENGTH_SHORT).show();
    }

    //Cargar registros desde el archivo
    private HashMap<String, SimilarityClassifier.Recognition> readFromSP(){
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, SimilarityClassifier.Recognition>());
        String json=sharedPreferences.getString("map",defValue);

        TypeToken<HashMap<String,SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String,SimilarityClassifier.Recognition>>() {};
        HashMap<String,SimilarityClassifier.Recognition> retrievedMap=new Gson().fromJson(json,token.getType());

        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet()) {
            float[][] output=new float[1][OUTPUT_SIZE];
            ArrayList arrayList= (ArrayList) entry.getValue().getExtra();
            arrayList = (ArrayList) arrayList.get(0);

            for (int counter = 0; counter < arrayList.size(); counter++) {
                output[0][counter]= ((Double) arrayList.get(counter)).floatValue();
            }
            entry.getValue().setExtra(output);
        }
        Toast.makeText(context, "Registros cargados", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    //Cargar un archivo local
    private void loadphoto(){
        start = false;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Elija una fotografia"), SELECT_PICTURE);
    }

    //Analisis de la imagen
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                try {
                    InputImage impphoto=InputImage.fromBitmap(getBitmapFromUri(selectedImageUri),0);
                    detector.process(impphoto).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {

                            if(faces.size()!=0) {
                                recognize.setText("Recognize");
                                add_face.setVisibility(View.VISIBLE);
                                reco_name.setVisibility(View.INVISIBLE);
                                face_preview.setVisibility(View.VISIBLE);
                                preview_info.setText(
                                        "\nInstrucciones \n\n" +
                                                "1. Colocar el rostro en frente.\n\n" +
                                                "2. Una vez reconocido su rostro aparecera aca.\n\n" +
                                                "3. Guardar");
                                Face face = faces.get(0);

                                System.out.println(face);

                                Bitmap frame_bmp= null;
                                try {
                                    frame_bmp = getBitmapFromUri(selectedImageUri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Bitmap frame_bmp1 = rotateBitmap(frame_bmp, 0, flipX, false);
                                face_preview.setImageBitmap(frame_bmp1);

                                RectF boundingBox = new RectF(face.getBoundingBox());

                                Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                // face_preview.setImageBitmap(scaled);

                                    recognizeImage(scaled);
                                    addFace();
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            start=true;
                            Toast.makeText(context, "Hubo un problema al guardar el registro", Toast.LENGTH_SHORT).show();
                        }
                    });
                    face_preview.setImageBitmap(getBitmapFromUri(selectedImageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        //Text to Speech
        if (requestCode == MY_DATA_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                mTts = new TextToSpeech(this, this);
            } else {
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    private TextToSpeech mTts;

    //Idioma del Text to Speech
    public void onInit(int i){
        mTts.setLanguage(new Locale("spa", "ESP"));
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }
}

