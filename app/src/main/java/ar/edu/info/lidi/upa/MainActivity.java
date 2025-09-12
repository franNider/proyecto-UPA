package ar.edu.info.lidi.upa;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.graphics.BitmapFactory;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Arrays;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import ar.edu.info.lidi.upa.assist.PositionAssistanceInterface;
import ar.edu.info.lidi.upa.assist.ProcessCompletedCallBackInterface;
import ar.edu.info.lidi.upa.common.ClassroomStatusFactory;
import ar.edu.info.lidi.upa.common.Observer;
import ar.edu.info.lidi.upa.common.PositionAssistanceFactory;
import ar.edu.info.lidi.upa.exception.ProcessingException;
import ar.edu.info.lidi.upa.tts.TTSListener;
import ar.edu.info.lidi.upa.utils.Constants;
import ar.edu.info.lidi.upa.utils.JSONExporter;
import ar.edu.info.lidi.upa.utils.JSONImporter;

import android.graphics.Canvas;
import android.graphics.Color;

public class MainActivity extends AppCompatActivity implements ProcessCompletedCallBackInterface, Observer {

    protected final String TAG = "UPA";
    private static final int REQUEST_CAMERA_PERMISSION = 101;

    TTSListener listener;
    TextToSpeech tts;

    Toolbar toolbar;
    Button dondeEstoyButton;
    Button entrenarButton;
    Button eliminarButton;
    Button cerrarButton;
    Button exportarAlClipboardButton;
    Button importarDelClipboardButton;
    Button compartir;
    Button mostrarDatos;
    EditText ubicacionEditText;
    EditText datosEditText;
    Spinner iterationsSpinner;
    ScrollView scrollView;

    PositionAssistanceInterface posAssist;
    JSONExporter exporter;
    JSONImporter importer;

    int totalIterations = 0;

    private static final int REQUEST_IMAGE_CAPTURE = 3;
    private Uri photoUri;
    private String currentPhotoPath;

    private GestureDetector gestureDetector;
    private static final int REQUEST_IMAGE_PICK = 2;

    private static final int MODEL_INPUT_SIZE = 224;
    private static final int NUM_CLASSES = 20;
    private Interpreter tflite;
    private Bitmap capturedImageBitmap;

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void abrirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        lanzarIntentCamara();
    }

/*    private void lanzarIntentCamara() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "No se pudo crear el archivo de imagen", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error al crear el archivo de imagen: " + ex.getMessage());
                return;
            }

            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            Toast.makeText(this, "No hay aplicación de cámara disponible", Toast.LENGTH_SHORT).show();
        }
    }
*/
    private void lanzarIntentCamara() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Especificar que se use la cámara trasera
        takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 0); // 0 = cámara trasera
        takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 0); // 0 = no frontal

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "No se pudo crear el archivo de imagen", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error al crear el archivo de imagen: " + ex.getMessage());
                return;
            }

            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                // Asegurar que se use la cámara trasera
                takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 0);

                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            Toast.makeText(this, "No hay aplicación de cámara disponible", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    public void update() {}

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 50;
        private static final int SWIPE_VELOCITY_THRESHOLD = 50;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        estimateLocation();
                        return true;
                    } else if (diffX > 0) {
                        abrirCamara();
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initComponents();
        initEvents();
        goAssistState();
        loadPreferences();
        initClassroomStatusService();

        gestureDetector = new GestureDetector(this, new GestureListener());
    }

    protected void initComponents() {
        posAssist = PositionAssistanceFactory.getInstance();
        exporter = new JSONExporter();
        importer = new JSONImporter();

        toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle("UPA");
        toolbar.inflateMenu(R.menu.options);

        dondeEstoyButton = findViewById(R.id.dondeEstoyButton);
        entrenarButton = findViewById(R.id.entrenarButton);
        eliminarButton = findViewById(R.id.eliminarButton);
        exportarAlClipboardButton = findViewById(R.id.exportartAlClipboardButton);
        importarDelClipboardButton = findViewById(R.id.importarDelClipboardButton);
        compartir = findViewById(R.id.compartir);
        mostrarDatos = findViewById(R.id.mostrarDatos);
        cerrarButton = findViewById(R.id.cerrarButton);
        ubicacionEditText = findViewById(R.id.ubicacionEditText);
        iterationsSpinner = findViewById(R.id.iterationsSpinner);
        datosEditText = findViewById(R.id.datosEditText);
        scrollView = findViewById(R.id.mainScrollView);

        String[] opciones = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opciones);
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        iterationsSpinner.setAdapter(adaptador);
        iterationsSpinner.setSelection(1);
        datosEditText.setVisibility(View.GONE);

        listener = new TTSListener();
        tts = new TextToSpeech(getBaseContext(), listener);

        posAssist.addObserver(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    protected void initEvents() {
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.ajustes) {
                goConfigState();
            }
            return false;
        });
        cerrarButton.setOnClickListener(v -> goAssistState());
        dondeEstoyButton.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        entrenarButton.setOnClickListener(v -> startTraining());
        eliminarButton.setOnClickListener(v -> deleteTraining());
        exportarAlClipboardButton.setOnClickListener(v -> exportToClipboard());
        importarDelClipboardButton.setOnClickListener(v -> importFromClipboard());
        compartir.setOnClickListener(v -> share());
        mostrarDatos.setOnClickListener(v -> showHideData());

        scrollView.setOnTouchListener((v, event) -> {
            datosEditText.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        datosEditText.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lanzarIntentCamara();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Uri imageUri = null;

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && photoUri != null) {
            imageUri = photoUri;
        }

        if (imageUri != null) {
            try {
                // Cargar la imagen con la máxima calidad posible
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1; // Sin reducción de muestreo
                options.inPreferredConfig = Bitmap.Config.ARGB_8888; // Máxima calidad

                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
                if (inputStream != null) inputStream.close();

                if (originalBitmap != null) {
                    // Ejecutar la inferencia en un hilo separado para no bloquear la UI
                    new Thread(() -> {
                        runModelInference(originalBitmap);
                    }).start();
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show());
                }

            } catch (IOException e) {
                Log.e(TAG, "Error al cargar la imagen: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Error inesperado: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Error inesperado", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        // El modelo espera imágenes de 224x224 con 3 canales (RGB)
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        // Redimensionar manteniendo la relación de aspecto
        Bitmap resizedBitmap = resizeBitmapKeepAspect(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);

        int[] intValues = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        resizedBitmap.getPixels(intValues, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);

        // Procesar píxeles igual que en Colab (normalización 0-1)
        for (int pixelValue : intValues) {
            // Normalizar a [0, 1] como en Colab: /255.0
            float r = ((pixelValue >> 16) & 0xFF) / 255.0f;
            float g = ((pixelValue >> 8) & 0xFF) / 255.0f;
            float b = (pixelValue & 0xFF) / 255.0f;

            byteBuffer.putFloat(r);
            byteBuffer.putFloat(g);
            byteBuffer.putFloat(b);
        }

        return byteBuffer;
    }

    private Bitmap resizeBitmapKeepAspect(Bitmap source, int targetWidth, int targetHeight) {
        int originalWidth = source.getWidth();
        int originalHeight = source.getHeight();

        // Calcular escala manteniendo relación de aspecto
        float scale = Math.min((float) targetWidth / originalWidth, (float) targetHeight / originalHeight);

        int scaledWidth = Math.round(originalWidth * scale);
        int scaledHeight = Math.round(originalHeight * scale);

        // Crear bitmap escalado
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);

        // Crear bitmap final con padding si es necesario
        Bitmap finalBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBitmap);
        canvas.drawColor(Color.BLACK); // Fondo negro como padding

        // Centrar la imagen escalada
        float left = (targetWidth - scaledWidth) / 2.0f;
        float top = (targetHeight - scaledHeight) / 2.0f;
        canvas.drawBitmap(scaledBitmap, left, top, null);

        return finalBitmap;
    }

    private void runModelInference(Bitmap bitmap) {
        if (bitmap == null) {
            runOnUiThread(() -> status("Imagen no válida", Constants.OUTPUT_TEXT));
            return;
        }

        try {
            Log.d("UPA", "Iniciando procesamiento de imagen: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            if (tflite == null) {
                try {
                    MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(this, "modelo.tflite");
                    Interpreter.Options options = new Interpreter.Options();
                    tflite = new Interpreter(tfliteModel, options);
                    Log.d("UPA", "✅ Modelo cargado exitosamente");
                } catch (IOException e) {
                    Log.e("UPA", "❌ Error cargando modelo: " + e.getMessage());
                    runOnUiThread(() -> status("Error cargando modelo", Constants.OUTPUT_TEXT));
                    return;
                }
            }

            ByteBuffer inputBuffer = convertBitmapToByteBuffer(bitmap);
            Log.d("UPA", "✅ Buffer de entrada creado");

            float[][] output = new float[1][NUM_CLASSES];

            long startTime = System.currentTimeMillis();
            tflite.run(inputBuffer, output);
            long endTime = System.currentTimeMillis();

            Log.d("UPA", "✅ Inferencia completada en " + (endTime - startTime) + "ms");

            // Procesar resultados...
            int maxIndex = 0;
            float maxProb = output[0][0];
            for (int i = 1; i < NUM_CLASSES; i++) {
                if (output[0][i] > maxProb) {
                    maxProb = output[0][i];
                    maxIndex = i;
                }
            }

            // Calcular diferencia con segunda predicción
            float[] sortedProbs = output[0].clone();
            Arrays.sort(sortedProbs);
            float confidenceDiff = sortedProbs[NUM_CLASSES-1] - sortedProbs[NUM_CLASSES-2];

            // Umbrales de confianza
            final float CONFIDENCE_THRESHOLD = 0.60f;
            final float TOP_DIFF_THRESHOLD = 0.2f;

            String[] labels = {"BañosSinGenero", "Aula10B", "AscensoresPlantaBaja", "Fotocopiadora", "PisoUnoBaños", "SalaPC", "Anfiteatro", "Aulas14y15", "Entrada", "Biblioteca", "Cefi", "Alumnos", "PlantaBajaBaños", "PisoUnoAulas1a4", "Lifia", "Decanato", "PisoUnoAscensores", "Aula5", "Buffet", "PlantaBajaAulas1a4"};

            String location;
            if (maxProb < CONFIDENCE_THRESHOLD || confidenceDiff < TOP_DIFF_THRESHOLD) {
                location = "NO_RECONOCIDO";
            } else {
                location = maxIndex >= 0 && maxIndex < labels.length ? labels[maxIndex] : "NO_RECONOCIDO";
            }

            final String finalLocation = location;
            final float finalMaxProb = maxProb;
            runOnUiThread(() ->
                    status("Ubicación detectada: " + finalLocation + " (" + String.format("%.2f", finalMaxProb * 100) + "%)", Constants.OUTPUT_BOTH)
            );

        } catch (Exception e) {
            Log.e("UPA", "❌ Error en runModelInference: " + e.getMessage());
            e.printStackTrace();
            runOnUiThread(() -> status("Error en reconocimiento", Constants.OUTPUT_TEXT));
        }
    }
    private String getLabelForIndex(int index) {
        String[] labels = {"BañosSinGenero", "Aula10B", "PB_Ascensores", "Fotocopiadora", "P1_Baños", "SalaPC", "Anfiteatro", "Aulas14-15", "Entrada", "Biblioteca", "Cefi", "Alumnos", "PB_Baños", "p1_Aulas1-4", "Lifia", "Decanato", "P1_Ascensores", "Aula5", "Buffet", "PB_Aulas1-4"};
        if (index >= 0 && index < labels.length) {
            return labels[index];
        }
        return "NO_RECONOCIDO";
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    protected void goAssistState() {
        status("UPA", Constants.OUTPUT_TEXT);
        dondeEstoyButton.setVisibility(View.VISIBLE);
        dondeEstoyButton.getLayoutParams().height = -1;
    }

    protected void loadPreferences() {
        SharedPreferences preferences = getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        ubicacionEditText.setText(preferences.getString(Constants.PREFERENCE_LOCATION, "Ubicación"));
        iterationsSpinner.setSelection(preferences.getInt(Constants.PREFERENCE_ITERATIONS, 1));
        try {
            posAssist.setTrainingSet(importer.fromJSON(preferences.getString(Constants.PREFERENCE_DATA, "")));
        } catch (Exception e) {
            // Ignorar error al cargar datos
        }
    }

    protected void savePreferences() {
        SharedPreferences preferences = getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(Constants.PREFERENCE_LOCATION, ubicacionEditText.getText().toString());
        editor.putInt(Constants.PREFERENCE_ITERATIONS, iterationsSpinner.getSelectedItemPosition());
        editor.putString(Constants.PREFERENCE_DATA, datosEditText.getText().toString());
        editor.apply();
    }

    protected void initClassroomStatusService() {
        try {
            ClassroomStatusFactory.getInstance().getCurrentStateFor("FOOBAR");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePreferences();
    }

    protected void goConfigState() {
        status("Ajustes", Constants.OUTPUT_TEXT);
        dondeEstoyButton.setVisibility(View.GONE);
    }

    protected void showHideData() {
        datosEditText.setVisibility(datosEditText.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
    }

    protected void startTraining() {
        if (ubicacionEditText.getText().toString().trim().length() == 0) {
            status("Se requiere ubicación", Constants.OUTPUT_TEXT);
            return;
        }
        totalIterations = Integer.parseInt(iterationsSpinner.getSelectedItem().toString());
        status("Entrenando...", Constants.OUTPUT_TEXT);
        train();
    }

    public void deleteTraining() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Confirmación")
                .setMessage("¿Estás seguro de que deseas eliminar?")
                .setPositiveButton("Aceptar", (aDialog, which) -> posAssist.emptyTrainingSet())
                .setNegativeButton("Cancelar", (aDialog, which) -> {})
                .create();
        dialog.show();
    }

    public void train() {
        posAssist.train(getBaseContext(), ubicacionEditText.getText().toString(), this);
    }

    public void estimateLocation() {
        status("Calculando... ", Constants.OUTPUT_BOTH);
        posAssist.locate(getBaseContext(), this);
    }

    protected void status(String message, Integer output) {
        if (Constants.OUTPUT_TEXT == output || Constants.OUTPUT_BOTH == output)
            toolbar.setSubtitle(message);
        if (Constants.OUTPUT_AUDIO == output || Constants.OUTPUT_BOTH == output)
            tts.speak(message, TextToSpeech.QUEUE_ADD, null, "" + System.nanoTime());
    }

    @Override
    public void trainingCompleted(String message) {
        Integer currentIteration = Integer.parseInt(iterationsSpinner.getSelectedItem().toString());
        if (currentIteration > 0) {
            iterationsSpinner.setSelection(iterationsSpinner.getSelectedItemPosition() - 1);
            train();
            return;
        }
        iterationsSpinner.setSelection(totalIterations);
        status(message, Constants.OUTPUT_TEXT);
    }

    @Override
    public void estimationCompleted(String message) {
        status(message, Constants.OUTPUT_BOTH);
        try {
            String info = ClassroomStatusFactory.getInstance().getCurrentStateFor(message);
            status(info, Constants.OUTPUT_BOTH);
        } catch (ProcessingException e) {}
    }

    @Override
    public void processingError(ProcessingException ex) {
        status(ex.getMessage(), Constants.OUTPUT_BOTH);
        ex.getExDetails().ifPresent(x -> Log.e(TAG, x.getMessage()));
    }

    protected void exportToClipboard() {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("text", exporter.toJSON(posAssist.getTrainingSet()));
            clipboardManager.setPrimaryClip(clip);
            status("Copiado al clipboard!", Constants.OUTPUT_TEXT);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            status("ERR: " + e.getMessage(), Constants.OUTPUT_TEXT);
        }
    }

    protected void importFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                posAssist.setTrainingSet(importer.fromJSON(item.getText().toString()));
                status("Copiado desde el clipboard!", Constants.OUTPUT_TEXT);
                return;
            }
            status("Formato inválido", Constants.OUTPUT_TEXT);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            status("ERR: " + e.getMessage(), Constants.OUTPUT_TEXT);
        }
    }

    protected void share() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, exporter.toJSON(posAssist.getTrainingSet()));
            startActivity(Intent.createChooser(shareIntent, "Compartir datos"));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            status("ERR: " + e.getMessage(), Constants.OUTPUT_TEXT);
        }
    }
}