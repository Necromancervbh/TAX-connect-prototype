package com.example.taxconnect;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.taxconnect.databinding.ActivitySecureDocViewerBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecureDocViewerActivity extends AppCompatActivity {

    private ActivitySecureDocViewerBinding binding;
    private String docUrl;
    private String docType;
    private File tempFile;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // PREVENT SCREENSHOTS
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        binding = ActivitySecureDocViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        docUrl = getIntent().getStringExtra("DOC_URL");
        docType = getIntent().getStringExtra("DOC_TYPE");
        String title = getIntent().getStringExtra("DOC_TITLE");
        if (title != null) binding.tvTitle.setText(title);

        if (docUrl == null) {
            Toast.makeText(this, "Invalid Document", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadDocument();
    }

    private void loadDocument() {
        binding.progressBar.setVisibility(View.VISIBLE);

        if ("image".equalsIgnoreCase(docType)) {
            binding.rvPdfPages.setVisibility(View.GONE);
            binding.ivDoc.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(docUrl)
                    .into(binding.ivDoc);
            binding.progressBar.setVisibility(View.GONE);
        } else if ("pdf".equalsIgnoreCase(docType)) {
            binding.ivDoc.setVisibility(View.GONE);
            binding.rvPdfPages.setVisibility(View.VISIBLE);
            downloadAndRenderPdf(docUrl);
        } else {
            // Default to image if unknown, or try to load as image
             binding.rvPdfPages.setVisibility(View.GONE);
            binding.ivDoc.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(docUrl)
                    .into(binding.ivDoc);
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void downloadAndRenderPdf(String urlString) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream input = connection.getInputStream();

                tempFile = new File(getCacheDir(), "secure_view_" + System.currentTimeMillis() + ".pdf");
                FileOutputStream output = new FileOutputStream(tempFile);

                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                output.close();
                input.close();

                runOnUiThread(this::renderPdf);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
                    binding.progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void renderPdf() {
        try {
            fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);

            PdfPageAdapter adapter = new PdfPageAdapter(pdfRenderer);
            binding.rvPdfPages.setLayoutManager(new LinearLayoutManager(this));
            binding.rvPdfPages.setAdapter(adapter);
            
            binding.progressBar.setVisibility(View.GONE);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error rendering PDF", Toast.LENGTH_SHORT).show();
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageViewHolder> {
        private final PdfRenderer renderer;

        public PdfPageAdapter(PdfRenderer renderer) {
            this.renderer = renderer;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new PageViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            PdfRenderer.Page page = renderer.openPage(position);
            
            // High quality rendering
            int width = page.getWidth() * 2;
            int height = page.getHeight() * 2;
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            
            ((ImageView) holder.itemView).setImageBitmap(bitmap);
            
            page.close();
        }

        @Override
        public int getItemCount() {
            return renderer.getPageCount();
        }

        static class PageViewHolder extends RecyclerView.ViewHolder {
            public PageViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
