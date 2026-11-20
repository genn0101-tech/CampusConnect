// Suggestions on the check-out page (only loaded on the event day).
// While the person types a name (2+ letters) or a Student ID (4+ characters),
// ask the server for matching people. The server only ever sends back names.
// Tapping a name sends the name form, which checks that person out.
(function () {
    const form = document.getElementById("name-form");
    const nameInput = document.getElementById("name-input");
    const idPrefix = document.getElementById("id-prefix");
    const url = form.dataset.candidatesUrl;

    // Typing a name: ?q=...  Tapping a suggestion sends the name only.
    suggest(nameInput, document.getElementById("name-candidates"), "q", 2, function (name) {
        idPrefix.value = "";
        nameInput.value = name;
        form.submit();
    });

    // Typing a Student ID: ?id=...  Tapping a suggestion also sends the typed digits,
    // so the server can tell apart two people with the same name.
    const idInput = document.getElementById("id-input");
    suggest(idInput, document.getElementById("id-candidates"), "id", 4, function (name) {
        idPrefix.value = idInput.value.trim();
        nameInput.value = name;
        form.submit();
    });

    /** Shows suggestion buttons under one input. */
    function suggest(input, list, param, minLength, onPick) {
        let timer = null;
        input.addEventListener("input", function () {
            // Wait 250 ms after the last key, so we do not send a request for every letter
            clearTimeout(timer);
            timer = setTimeout(load, 250);
        });

        async function load() {
            const typed = input.value.trim();
            if (typed.length < minLength) {
                show([]);
                return;
            }
            const response = await fetch(url + "?" + param + "=" + encodeURIComponent(typed));
            if (response.ok) {
                show(await response.json());
            }
        }

        function show(names) {
            list.innerHTML = "";
            for (const name of names) {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = name; // textContent (not innerHTML) so a name can never run as code
                button.addEventListener("click", function () {
                    onPick(name);
                });
                const item = document.createElement("li");
                item.appendChild(button);
                list.appendChild(item);
            }
        }
    }
})();
