const admin = require('firebase-admin');
const fs = require('fs');

admin.initializeApp({ projectId: 'trae1-d90c2' });
const db = admin.firestore();

const out = [];
const log = (s) => out.push(s);

async function auditUsers() {
    const snap = await db.collection('users').limit(4).get();
    log('\n=== USERS COLLECTION ===');
    log(`Total sampled: ${snap.size}`);
    snap.forEach(doc => {
        const d = doc.data();
        log(`\nDoc: ${doc.id}`);
        log(`  name: ${d.name}`);
        log(`  email: ${d.email}`);
        log(`  role: ${d.role}`);
        log(`  rating: ${d.rating}`);
        log(`  ratingCount: ${d.ratingCount}`);
        log(`  isOnline: ${d.isOnline}`);
        log(`  uid: ${d.uid}`);
        log(`  fcmToken_present: ${!!d.fcmToken}`);
        log(`  profileImageUrl_present: ${!!d.profileImageUrl}`);
        log(`  specialization: ${d.specialization}`);
        log(`  experience: ${d.experience}`);
        log(`  ALL_FIELDS: [${Object.keys(d).join(', ')}]`);
    });

    // Check CA users specifically
    const caSnap = await db.collection('users').where('role', '==', 'CA').limit(2).get();
    log(`\nCA users with role==CA: ${caSnap.size}`);
    caSnap.forEach(doc => {
        log(`  CA: ${doc.id} | name: ${doc.data().name} | rating: ${doc.data().rating} | ratingCount: ${doc.data().ratingCount}`);
        // Check ratings subcollection
    });

    // Check ratings subcollections
    if (!caSnap.empty) {
        const caId = caSnap.docs[0].id;
        const ratingsSnap = await db.collection('users').doc(caId).collection('ratings').limit(3).get();
        log(`\nRatings subcollection for CA ${caId}: ${ratingsSnap.size} docs`);
        ratingsSnap.forEach(r => {
            const d = r.data();
            log(`  rating: ${d.rating}, userId: ${d.userId}, caId: ${d.caId}, timestamp: ${d.timestamp}`);
        });
    }
}

async function auditConversations() {
    const snap = await db.collection('conversations').limit(3).get();
    log('\n=== CONVERSATIONS COLLECTION ===');
    log(`Total sampled: ${snap.size}`);
    snap.forEach(doc => {
        const d = doc.data();
        log(`\nConv: ${doc.id}`);
        log(`  workflowState: ${d.workflowState}`);
        log(`  participantIds: ${JSON.stringify(d.participantIds)}`);
        log(`  videoCallAllowed: ${d.videoCallAllowed}`);
        log(`  lastMessage: ${(d.lastMessage || '').substring(0, 50)}`);
        log(`  ALL_FIELDS: [${Object.keys(d).join(', ')}]`);
    });

    // Messages subcollection
    if (!snap.empty) {
        const convId = snap.docs[0].id;
        const msgSnap = await db.collection('conversations').doc(convId).collection('messages').limit(5).get();
        log(`\nMessages in conv ${convId}: ${msgSnap.size}`);
        msgSnap.forEach(m => {
            const d = m.data();
            log(`  type: ${d.type || 'MISSING'}, senderId: ${(d.senderId || '').substring(0, 12)}, msg: "${(d.message || '').substring(0, 50)}"`);
        });

        // Check all message types
        const allMsgTypes = new Set();
        const allSnap = await db.collection('conversations').doc(convId).collection('messages').get();
        allSnap.forEach(m => allMsgTypes.add(m.data().type));
        log(`  All message types in this conv: [${[...allMsgTypes].join(', ')}]`);
    }
}

async function auditServices() {
    const snap = await db.collection('services').limit(2).get();
    log('\n=== SERVICES COLLECTION ===');
    log(`Total docs: ${snap.size}`);
    if (!snap.empty) snap.forEach(doc => log(`  Doc: ${doc.id}, fields: [${Object.keys(doc.data()).join(', ')}]`));
}

async function main() {
    await auditUsers();
    await auditConversations();
    await auditServices();
    log('\n=== AUDIT COMPLETE ===');
    fs.writeFileSync('audit_result.txt', out.join('\n'), 'utf8');
    console.log(out.join('\n'));
    process.exit(0);
}

main().catch(e => { console.error(e.message); process.exit(1); });
