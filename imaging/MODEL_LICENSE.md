# Segmentation model

- File: `src/main/assets/deeplabv3_257_mv_gpu.tflite` (2,779,264 bytes)
- Source: https://storage.googleapis.com/download.tensorflow.org/models/tflite/gpu/deeplabv3_257_mv_gpu.tflite
  (official TensorFlow Lite GPU example model, DeepLab v3 with a MobileNetV2 backbone, PASCAL VOC 2012, 21 classes)
- Upstream project: https://github.com/tensorflow/models/tree/master/research/deeplab (DeepLab) and
  https://www.tensorflow.org/lite/examples/segmentation/overview
- License: Apache License 2.0, https://www.apache.org/licenses/LICENSE-2.0
- Input: float32 `[1, 257, 257, 3]`, RGB normalised as `(c / 127.5) - 1`.
- Output: float32 `[1, 257, 257, 21]` class scores. The code reads the real tensor shapes at runtime and also
  accepts a `[1, H, W]` class-index output.
- The file is used unmodified. It is not copied from any third-party application.
