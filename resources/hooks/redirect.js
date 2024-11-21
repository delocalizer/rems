/*jslint browser:true */

window.rems = window.rems || {};
window.rems.hooks = window.rems.hooks || {};

window.rems.hooks.navigate = function (path) {
    // info
    console.log("I navigate to " + path);
};

window.rems.hooks.get = function (path, other) {
    // info
    console.log("I get " + path);
};

window.rems.hooks.put = function (path, other) {
    // info
    console.log("I put " + path);
    console.log(JSON.stringify(other));

    // the app calls /save-draft immediately before /submit
    if (path.startsWith("/api/applications/save-draft")) {
        // In test-data setup, form 2 is "Example form with all field types"
        // and fld3 is "Text field". For quick testing I decided this would
        // be used to capture a redirect URL. Of course in real life it would
        // have its own properly identified field.
        window.redirectAfterSubmit = other.params["field-values"].find(
            function (e) { return e.form === 2 && e.field === "fld3"; }
        );
    }

    if (path.startsWith("/api/applications/submit") && window.redirectAfterSubmit) {
        console.log("I will try to redirect to " + window.redirectAfterSubmit.value);
        try {
            window.location.href = new URL(window.redirectAfterSubmit.value);
        } catch (err) {
            console.log("Not a valid redirect: " + window.redirectAfterSubmit.value);
        }
    }
};
