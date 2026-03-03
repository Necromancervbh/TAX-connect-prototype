describe("Call flow", () => {
  it("handles notification to hangup", () => {
    cy.visit("/");
    cy.get('[data-testid="simulate-notification"]').click();
    cy.get('[data-testid="answer-call"]').click();
    cy.get('[data-testid="toggle-mic"]').click();
    cy.get('[data-testid="toggle-camera"]').click();
    cy.get('[data-testid="hang-up"]').click();
    cy.get('[data-testid="simulate-notification"]').should("exist");
  });
});
