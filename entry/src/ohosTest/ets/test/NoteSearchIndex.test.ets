import { describe, expect, it } from '@ohos/hypium';
import { Note } from '../../../main/ets/model/Note';
import { NoteSearchIndex } from '../../../main/ets/model/NoteSearchIndex';

export default function noteSearchIndexTest(): void {
  describe('NoteSearchIndex', () => {
    it('finds title and body keywords without changing source order', 0, () => {
      const index = new NoteSearchIndex();
      index.rebuild([
        new Note('2', 'HarmonyOS', '# HarmonyOS\n拍照笔记', 2),
        new Note('1', 'Shopping', '# Shopping\nBuy Coffee', 1)
      ]);

      expect(index.search('拍照')[0].id).assertEqual('2');
      expect(index.search(' coffee ')[0].id).assertEqual('1');
      expect(index.search('').length).assertEqual(2);
    });

    it('rebuilds from a 1000-note Markdown library', 0, () => {
      const notes: Array<Note> = [];
      for (let index = 0; index < 1000; index += 1) {
        notes.push(new Note(
          `${index}`,
          `Note ${index}`,
          `# Note ${index}\nordinary content`,
          index
        ));
      }
      notes[731] = new Note(
        '731',
        'Needle',
        '# Needle\nunique searchable phrase',
        731
      );
      const searchIndex = new NoteSearchIndex();

      const startedAt = Date.now();
      searchIndex.rebuild(notes);
      const results = searchIndex.search('unique searchable');
      const elapsed = Date.now() - startedAt;

      expect(results.length).assertEqual(1);
      expect(results[0].id).assertEqual('731');
      expect(elapsed < 500).assertTrue();
    });
  });
}
