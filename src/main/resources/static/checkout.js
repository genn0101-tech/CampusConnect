// Name suggestions on the check-out page.
// While the person types, ask the server for matching names and show them as buttons.
// Tapping a name fills the input and sends the form.
(function () {
    const form = document.getElementById("name-form");
    const input = document.getElementById("name-input");
    const list = document.getElementById("name-candidates");
    const url = form.dataset.candidatesUrl;
    let timer = null;

    input.addEventListener("input", function () {
        // Wait 250 ms after the last key, so we do not send a request for every letter
        clearTimeout(timer);
        timer = setTimeout(loadCandidates, 250);
    });

    async function loadCandidates() {
        const typed = input.value.trim();
        if (typed.length < 2) {
            showNames([]);
            return;
        }
        const response = await fetch(url + "?q=" + encodeURIComponent(typed));
        if (response.ok) {
            showNames(await response.json());
        }
    }

    function showNames(names) {
        list.innerHTML = "";
        for (const name of names) {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = name; // textContent (not innerHTML) so a name can never run as code
            button.addEventListener("click", function () {
                input.value = name;
                form.submit();
            });
            const item = document.createElement("li");
            item.appendChild(button);
            list.appendChild(item);
        }
    }
})();
