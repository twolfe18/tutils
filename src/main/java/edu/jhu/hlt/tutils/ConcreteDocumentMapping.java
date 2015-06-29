package edu.jhu.hlt.tutils;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.uuid.UUIDFactory;
import edu.jhu.hlt.tutils.Document.Constituent;
import edu.jhu.hlt.tutils.Document.ConstituentItr;

/**
 * Maintains a mapping between items in a {@link Document} and a {@link Communication}.
 *
 * NOTE: Not for storing {@link Sentence} UUIDs, use {@link Tokenization} UUIDs
 * instead.
 *
 * NOTE: Be careful about adding {@link ConstituentItr}s, as they are mutable.
 *
 * @author travis
 */
public class ConcreteDocumentMapping {

  // There are many items that are indexed by UUIDs in Concrete and Constituents
  // in tutils.Document, e.g. EntityMention, Entity, Tokenization, Section, etc
  private BiMap<Document.Constituent, UUID> items;

  // TODO add Map<UUID, Class> types; ?

  // An interesting one is Tokenization vs Sentence.
  // If I had my druthers they'd be combined (as they are in tutils.Document)
  // and if this is to be a bimap I can't have a Tokenization.UUID and a
  // Sentence.UUID point to the same constituent representing the sentence span.
  // Since TokenRefSequences use Tokenization.UUID, Sentences are clearly less
  // important.

  // One of the following must not be null
  private Communication communication;
  private UUID communicationUUID;

  private Document doc;

  public ConcreteDocumentMapping(Communication c, Document doc) {
    if (c == null)
      throw new IllegalArgumentException();
    this.items = HashBiMap.create();
    this.communication = c;
    this.doc = doc;
  }

  /** Useful if you want to save memory */
  public ConcreteDocumentMapping(UUID communicationUUID, Document doc) {
    if (communicationUUID == null)
      throw new IllegalArgumentException();
    this.items = HashBiMap.create();
    this.communicationUUID = communicationUUID;
    this.doc = doc;
  }

  public UUID getCommunicationUUID() {
    if (communicationUUID == null)
      return communication.getUuid();
    if (communication != null)
      assert communicationUUID.equals(communication.getUuid());
    return communicationUUID;
  }

  public Communication getCommunication() {
    assert communication != null;
    return communication;
  }

  public Document getDocument() {
    return doc;
  }

  public int size() {
    return items.size();
  }

  /**
   * @param cons
   * @param id should NOT be a {@link Sentence} UUID!
   */
  public void put(Document.Constituent cons, UUID id) {
    items.put(cons, id);
  }

  /**
   * @param id should NOT be a {@link Sentence} UUID!
   * @return
   */
  public Document.Constituent get(UUID id) {
    return items.inverse().get(id);
  }

  public UUID get(Document.Constituent cons) {
    return items.get(cons);
  }

  /**
   * Used for figuring out what {@link EntityMentionSet} your
   * {@link EntityMention}s belong to.
   */
  private Map<UUID, UUID> buildEntityMention2EntityMentionSetMapping() {
    Map<UUID, UUID> m = new HashMap<>();
    for (EntityMentionSet es : communication.getEntityMentionSetList()) {
      for (EntityMention em : es.getMentionList()) {
        UUID old = m.put(em.getUuid(), es.getUuid());
        if (old != null) {
          throw new RuntimeException("non-unique EntityMention UUID! "
              + em.getUuid() + " appears in " + old + " and " + es.getUuid());
        }
      }
    }
    return m;
  }

  /**
   * Adds the coreference labels pointed to by coref to the {@link Communication}
   * held by this mapping. Don't do this twice!
   */
  public void addCorefToCommunication(Constituent coref, String toolname) {
    if (doc != coref.getDocument())
      throw new IllegalArgumentException();
    if (communication != null)
      throw new IllegalStateException("must have pointer to Communication");

    /*
     * NOTE: Right now this doesn't support the case where all the Constituents
     * haven't been derived from existing EntityMentions. If this isn't the
     * case, the easy fix is to have a method which adds these EntityMentions
     * with new UUIDs and then adds those (Constituent, UUID) pairs to this
     * mapping.
     */

    Map<UUID, UUID> em2ems = buildEntityMention2EntityMentionSetMapping();

    EntitySet es = new EntitySet();
    es.setUuid(UUIDFactory.newUUID());
    es.setMetadata(new AnnotationMetadata());
    es.getMetadata().setTimestamp(System.currentTimeMillis() / 1000);
    es.getMetadata().setTool(toolname);
    communication.addToEntitySetList(es);
    for (ConstituentItr entity = doc.getConstituentItr(coref.getIndex());
        entity.isValid(); entity.gotoRightSib()) {
      Entity cEnt = new Entity();
      cEnt.setUuid(UUIDFactory.newUUID());
      cEnt.setType("???");
      es.addToEntityList(cEnt);
      for (ConstituentItr mention = doc.getConstituentItr(entity.getLeftChild());
          mention.isValid(); mention.gotoRightSib()) {

        // Look up the EntityMention that this mention corresponds to.
        Constituent key = doc.getConstituent(mention.getIndex());
        UUID emId = items.get(key);
        cEnt.addToMentionIdList(emId);

        // Update/check the EntityMentionSet
        UUID emsId = em2ems.get(emId);
        if (emsId == null)
          throw new RuntimeException();
        if (es.isSetMentionSetId())
          es.setMentionSetId(emsId);
        else if (!es.getMentionSetId().equals(emsId))
          throw new RuntimeException("no support for mixed EntityMentionSets");
      }
    }
  }
}
