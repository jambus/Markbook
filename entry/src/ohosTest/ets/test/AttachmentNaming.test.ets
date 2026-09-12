import { describe, expect, it } from '@ohos/hypium';
import {
  AttachmentNaming,
  AttachmentRecovery,
  AttachmentRecoveryAction
} from '../../../main/ets/model/AttachmentNaming';

export default function attachmentNamingTest(): void {
  describe('AttachmentNaming', () => {
    it('creates a UUID v4 from random bytes', 0, () => {
      const uuid = AttachmentNaming.uuidV4(new Uint8Array([
        0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
        0x08, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF
      ]));

      expect(uuid).assertEqual('00112233-4455-4677-8899-aabbccddeeff');
    });

    it('builds a relative Markdown path and matching MIME type', 0, () => {
      const descriptor = AttachmentNaming.photo(
        '00112233-4455-4677-8899-aabbccddeeff',
        'jpg'
      );

      expect(descriptor.fileName)
        .assertEqual('00112233-4455-4677-8899-aabbccddeeff-original.jpg');
      expect(descriptor.relativePath)
        .assertEqual('../attachments/00112233-4455-4677-8899-aabbccddeeff-original.jpg');
      expect(descriptor.markdown)
        .assertEqual('![照片](../attachments/00112233-4455-4677-8899-aabbccddeeff-original.jpg)');
      expect(descriptor.mimeType).assertEqual('image/jpeg');

      const corrected = AttachmentNaming.photo(
        '00112233-4455-4677-8899-aabbccddeeff',
        'jpg',
        'corrected'
      );
      expect(corrected.fileName)
        .assertEqual('00112233-4455-4677-8899-aabbccddeeff-corrected.jpg');
      expect(corrected.relativePath)
        .assertEqual('../attachments/00112233-4455-4677-8899-aabbccddeeff-corrected.jpg');
    });

    it('builds a compact capture ID for new attachments', 0, () => {
      const captureId = AttachmentNaming.shortPhotoId(
        new Date(2026, 6, 30, 14, 30, 15),
        new Uint8Array([0x00, 0x00, 0x00])
      );
      const descriptor = AttachmentNaming.photo(captureId, 'jpg', 'corrected');

      expect(captureId).assertEqual('20260730-143015-0000');
      expect(descriptor.fileName).assertEqual('20260730-143015-0000-corrected.jpg');
      expect(AttachmentNaming.isPhotoFileName(descriptor.fileName)).assertTrue();
    });

    it('rejects invalid IDs and unsupported formats', 0, () => {
      let invalidIdRejected: boolean = false;
      let invalidFormatRejected: boolean = false;
      try {
        AttachmentNaming.photo('../escape', 'jpg');
      } catch (error) {
        invalidIdRejected = true;
      }
      try {
        AttachmentNaming.photo(
          '00112233-4455-4677-8899-aabbccddeeff',
          'gif'
        );
      } catch (error) {
        invalidFormatRejected = true;
      }

      expect(invalidIdRejected).assertTrue();
      expect(invalidFormatRejected).assertTrue();
    });

    it('keeps committed references and deletes interrupted orphan files', 0, () => {
      const fileName = '00112233-4455-4677-8899-aabbccddeeff-original.jpg';
      expect(AttachmentRecovery.decide(
        fileName,
        true,
        [`# Note\n![照片](../attachments/${fileName})`]
      )).assertEqual(AttachmentRecoveryAction.KEEP);
      expect(AttachmentRecovery.decide(
        fileName,
        true,
        ['# Note without an attachment']
      )).assertEqual(AttachmentRecoveryAction.DELETE);
      expect(AttachmentRecovery.decide(
        'photo-00112233-4455-4677-8899-aabbccddeeff.jpg',
        true,
        ['# Existing note\n![照片](../attachments/photo-00112233-4455-4677-8899-aabbccddeeff.jpg)']
      )).assertEqual(AttachmentRecoveryAction.KEEP);
      expect(AttachmentRecovery.decide(
        '../outside.jpg',
        true,
        []
      )).assertEqual(AttachmentRecoveryAction.IGNORE);
    });
  });
}
