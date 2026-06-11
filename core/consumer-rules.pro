# Propagated to any app module that depends on :core.
# PDFBox-Android reflects over these classes when extracting PDF text.
-keep class com.tom_roush.pdfbox.** { *; }
