import type { DocumentReference, Firestore } from "firebase-admin/firestore";

const BATCH_DELETE_LIMIT = 450;

export async function deleteDocumentRefs(db: Firestore, refs: DocumentReference[]) {
  for (let index = 0; index < refs.length; index += BATCH_DELETE_LIMIT) {
    const batch = db.batch();
    refs.slice(index, index + BATCH_DELETE_LIMIT).forEach((ref) => batch.delete(ref));
    await batch.commit();
  }
}
