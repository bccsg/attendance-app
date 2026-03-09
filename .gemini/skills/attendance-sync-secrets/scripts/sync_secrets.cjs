#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const LOCAL_PROPERTIES_PATH = path.join(process.cwd(), 'local.properties');

function syncSecrets() {
  if (!fs.existsSync(LOCAL_PROPERTIES_PATH)) {
    console.error('Error: local.properties not found in the current directory.');
    process.exit(1);
  }

  const content = fs.readFileSync(LOCAL_PROPERTIES_PATH, 'utf8');
  const lines = content.split('\n');
  const secrets = {};

  lines.forEach(line => {
    const [key, ...valueParts] = line.split('=');
    if (key && valueParts.length > 0) {
      secrets[key.trim()] = valueParts.join('=').trim();
    }
  });

  const keysToSync = [
    'MASTER_SHEET_ID',
    'EVENT_SHEET_ID',
    'GOOGLE_CLIENT_SECRETS_JSON'
  ];

  let syncedCount = 0;

  keysToSync.forEach(key => {
    const value = secrets[key];
    if (value) {
      console.log(`Syncing ${key}...`);
      
      let finalValue = value;
      let targetKey = key;

      // Special handling for client secrets - the CI expects base64
      if (key === 'GOOGLE_CLIENT_SECRETS_JSON') {
        finalValue = Buffer.from(value).toString('base64');
        targetKey = 'GOOGLE_CLIENT_SECRETS_BASE64';
      }

      try {
        execSync(`gh secret set ${targetKey}`, {
          input: finalValue,
          encoding: 'utf8'
        });
        console.log(`✅ ${targetKey} set successfully.`);
        syncedCount++;
      } catch (error) {
        console.error(`❌ Failed to set ${targetKey}: ${error.message}`);
      }
    } else {
      console.warn(`⚠️ Warning: ${key} not found in local.properties. Skipping.`);
    }
  });

  console.log(`\nDone! Synced ${syncedCount} secrets.`);
}

try {
  // Check if gh is installed and authenticated
  execSync('gh auth status', { stdio: 'ignore' });
  syncSecrets();
} catch (error) {
  console.error('Error: GitHub CLI (gh) is not installed or not authenticated.');
  console.error('Please run "gh auth login" first.');
  process.exit(1);
}
