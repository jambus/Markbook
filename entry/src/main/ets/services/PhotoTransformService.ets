import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';

export class PhotoPoint {
  x: number;
  y: number;

  constructor(x: number, y: number) {
    this.x = x;
    this.y = y;
  }
}

class PhotoSize {
  width: number;
  height: number;

  constructor(width: number, height: number) {
    this.width = width;
    this.height = height;
  }
}

export class PhotoTransformService {
  static async writePerspectiveJpeg(
    sourcePath: string,
    targetPath: string,
    points: Array<PhotoPoint>
  ): Promise<void> {
    if (points.length !== 4) {
      throw new Error('Perspective transform requires four points');
    }
    const source = image.createImageSource(sourcePath);
    const pixelMap = await source.createPixelMap({
      desiredPixelFormat: image.PixelMapFormat.RGBA_8888,
      editable: false
    });
    const info = await pixelMap.getImageInfo();
    const width = info.size.width;
    const height = info.size.height;
    const sourceStride = width * 4;
    const sourceBuffer = new ArrayBuffer(sourceStride * height);
    await pixelMap.readPixelsToBuffer(sourceBuffer);
    const pixels = new Uint8Array(sourceBuffer);
    const outputSize = PhotoTransformService.outputSize(points, width, height);
    const matrix = PhotoTransformService.solveHomography(points, outputSize.width, outputSize.height,
      width, height);
    const output = new Uint8Array(outputSize.width * outputSize.height * 4);
    for (let y = 0; y < outputSize.height; y += 1) {
      for (let x = 0; x < outputSize.width; x += 1) {
        const denominator = matrix[6] * x + matrix[7] * y + 1;
        const sourceX = (matrix[0] * x + matrix[1] * y + matrix[2]) / denominator;
        const sourceY = (matrix[3] * x + matrix[4] * y + matrix[5]) / denominator;
        const destinationOffset = (y * outputSize.width + x) * 4;
        if (sourceX < 0 || sourceY < 0 || sourceX >= width || sourceY >= height) {
          output[destinationOffset + 3] = 255;
          continue;
        }
        const nearestX = Math.min(width - 1, Math.max(0, Math.round(sourceX)));
        const nearestY = Math.min(height - 1, Math.max(0, Math.round(sourceY)));
        const sourceOffset = nearestY * sourceStride + nearestX * 4;
        output[destinationOffset] = pixels[sourceOffset];
        output[destinationOffset + 1] = pixels[sourceOffset + 1];
        output[destinationOffset + 2] = pixels[sourceOffset + 2];
        output[destinationOffset + 3] = pixels[sourceOffset + 3] ?? 255;
      }
    }

    const result = await image.createPixelMap(output.buffer, {
      size: { width: outputSize.width, height: outputSize.height },
      pixelFormat: image.PixelMapFormat.RGBA_8888,
      alphaType: image.AlphaType.OPAQUE,
      editable: true
    });
    const file = fs.openSync(
      targetPath,
      fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC
    );
    try {
      const packer = image.createImagePacker();
      await packer.packToFile(result, file.fd, { format: 'image/jpeg', quality: 92 });
      await packer.release();
      fs.fsyncSync(file.fd);
    } finally {
      fs.closeSync(file);
      await result.release();
      await pixelMap.release();
      await source.release();
    }
  }

  private static outputSize(points: Array<PhotoPoint>, width: number, height: number): PhotoSize {
    const top = PhotoTransformService.distance(points[0], points[1], width, height);
    const bottom = PhotoTransformService.distance(points[3], points[2], width, height);
    const left = PhotoTransformService.distance(points[0], points[3], width, height);
    const right = PhotoTransformService.distance(points[1], points[2], width, height);
    return new PhotoSize(
      Math.max(2, Math.min(2400, Math.round(Math.max(top, bottom)))),
      Math.max(2, Math.min(2400, Math.round(Math.max(left, right))))
    );
  }

  private static distance(a: PhotoPoint, b: PhotoPoint, width: number, height: number): number {
    const x = (a.x - b.x) * width;
    const y = (a.y - b.y) * height;
    return Math.sqrt(x * x + y * y);
  }

  private static solveHomography(
    points: Array<PhotoPoint>,
    outputWidth: number,
    outputHeight: number,
    sourceWidth: number,
    sourceHeight: number
  ): Array<number> {
    const destination = [
      [0, 0], [outputWidth - 1, 0],
      [outputWidth - 1, outputHeight - 1], [0, outputHeight - 1]
    ];
    const matrix: Array<Array<number>> = [];
    for (let index = 0; index < 4; index += 1) {
      const u = destination[index][0];
      const v = destination[index][1];
      const x = points[index].x * sourceWidth;
      const y = points[index].y * sourceHeight;
      matrix.push([u, v, 1, 0, 0, 0, -u * x, -v * x, x]);
      matrix.push([0, 0, 0, u, v, 1, -u * y, -v * y, y]);
    }
    return PhotoTransformService.gaussianElimination(matrix);
  }

  private static gaussianElimination(matrix: Array<Array<number>>): Array<number> {
    for (let column = 0; column < 8; column += 1) {
      let pivot = column;
      for (let row = column + 1; row < 8; row += 1) {
        if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) {
          pivot = row;
        }
      }
      if (Math.abs(matrix[pivot][column]) < 0.000001) {
        throw new Error('Perspective points are degenerate');
      }
      const swap = matrix[column];
      matrix[column] = matrix[pivot];
      matrix[pivot] = swap;
      const divisor = matrix[column][column];
      for (let value = column; value < 9; value += 1) {
        matrix[column][value] /= divisor;
      }
      for (let row = 0; row < 8; row += 1) {
        if (row === column) {
          continue;
        }
        const factor = matrix[row][column];
        for (let value = column; value < 9; value += 1) {
          matrix[row][value] -= factor * matrix[column][value];
        }
      }
    }
    return [
      matrix[0][8], matrix[1][8], matrix[2][8],
      matrix[3][8], matrix[4][8], matrix[5][8],
      matrix[6][8], matrix[7][8], 1
    ];
  }
}
