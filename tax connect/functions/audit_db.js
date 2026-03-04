/**
 * TaxConnect Firestore Audit Script
 * Run with: node audit_db.js
 */
const admin = require('firebase-admin');

admin.initializeApp({
    projectId: 'trae1-d90c2'
});

const db = admin.firestore();

async function auditCollection(collectionName, sampleCount = 2) {
    console.log(`\n${'='.repeat(60)}`);
    console.log(`COLLECTION: ${collectionName}`);
    console.log('='.repeat(60));

    const snapshot = await db.collection(collectionName).limit(sampleCount).get();

    if (snapshot.empty) {
        console.log('  [EMPTY — no documents found]');
        return;
    }

    console.log(`  Total docs sampled: ${snapshot.size}`);
    snapshot.forEach(doc => {
        const data = doc.data();
        const fields = Object.keys(data);
        console.log(`\n  Doc ID: ${doc.id}`);
        console.log(`  Fields present (${fields.length}):`);
        fields.forEach(field => {
            const val = data[field];
            let display;
            if (val === null) display = 'null';
            else if (typeof val === 'object' && !Array.isArray(val)) display = `{object: ${Object.keys(val).join(', ')}}`;
            else if (Array.isArray(val)) display = `[array, len=${val.length}]`;
            else if (typeof val === 'string' && val.length > 60) display = `"${val.substring(0, 60)}..."`;
            else display = JSON.stringify(val);
            console.log(`    ${field}: ${display}`);
        });
    });
}

async function auditSubcollection(docPath, subName) {
    const snap = await db.doc(docPath).collection(subName).limit(3).get();
    if (!snap.empty) {
        console.log(`\n  SUBCOLLECTION '${subName}' (${snap.size} docs):`);
        snap.forEach(doc => {
            const fields = Object.keys(doc.data());
            console.log(`    Doc ${doc.id}: fields = [${fields.join(', ')}]`);
        });
    }
}

async function auditConversationMessages() {
    const convSnap = await db.collection('conversations').limit(2).get();
    for (const doc of convSnap.docs) {
        const msgSnap = await db.collection('conversations').doc(doc.id).collection('messages').limit(5).get();
        if (!msgSnap.empty) {
            console.log(`\n  Messages in conversation ${doc.id}:`);
            msgSnap.forEach(m => {
                const d = m.data();
                console.log(`    type=${d.type || 'MISSING'}, senderId=${d.senderId?.substring(0, 8)}..., msg="${(d.message || '').substring(0, 40)}"`);
            });
        }
    }
}

async function main() {
    try {
        console.log('\nTaxConnect Firestore Audit');
        console.log('Project: trae1-d90c2');
        console.log(new Date().toISOString());

        // 1. Users collection
        await auditCollection('users', 3);

        // Check ratings subcollection on first CA user
        const usersSnap = await db.collection('users').where('role', '==', 'CA').limit(1).get();
        if (!usersSnap.empty) {
            const caDoc = usersSnap.docs[0];
            console.log(`\nCA user found: ${caDoc.id}`);
            await auditSubcollection(`users/${caDoc.id}`, 'ratings');
        } else {
            console.log('\n[WARNING] No CA users found with role == "CA"');
        }

        // 2. Conversations collection
        await auditCollection('conversations', 2);

        // Check messages subcollection
        console.log('\nMessages subcollection audit:');
        await auditConversationMessages();

        // 3. Services collection (if exists)
        await auditCollection('services', 2);

        // 4. Notifications (if exists)
        try {
            await auditCollection('notifications', 2);
        } catch (_) {
            console.log('\n[INFO] No notifications collection found');
        }

        console.log('\n\nAudit complete!');
        process.exit(0);
    } catch (err) {
        console.error('Audit error:', err.message);
        process.exit(1);
    }
}

main();
