function canReleaseEscrow({ milestoneStatus, disputeStatus }) {
  if (disputeStatus === "OPEN") {
    return false;
  }
  return milestoneStatus === "WORK_COMMENCED";
}

function canPayBalance({ completionStatus, disputeStatus }) {
  if (disputeStatus === "OPEN") {
    return false;
  }
  return completionStatus === "COMPLETED";
}

module.exports = {
  canReleaseEscrow,
  canPayBalance
};
